package com.company.itam.user.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.user.entity.UserEntity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AccountImportService {
    private static final String[] HEADERS = {"Mã nhân viên / Employee code *", "Họ tên / Full name *", "Email",
        "Mật khẩu / Password (trống / blank: Pass@1234)", "Mã phòng ban / Department code"};
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    public record Item(int row, String employeeCode, String fullName, String email, String departmentCode,
                       String status, List<String> errors, Long id) {}
    public record Result(List<Item> rows, int valid, int invalid, int created) {}
    private record Candidate(int row, String code, String name, String email, String password, String department, List<String> parseErrors) {}

    private final IdentityService identities;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public AccountImportService(IdentityService identities, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.identities=identities; this.jdbc=jdbc;
        this.transactions=new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public byte[] template() {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            Sheet importSheet=book.createSheet("IMPORT");
            Row headers=importSheet.createRow(0);
            CellStyle heading=book.createCellStyle();heading.setWrapText(true);
            Font bold=book.createFont();bold.setBold(true);heading.setFont(bold);
            CellStyle text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));
            for(int i=0;i<HEADERS.length;i++) {var cell=headers.createCell(i,CellType.STRING);cell.setCellValue(HEADERS[i]);cell.setCellStyle(heading);importSheet.setColumnWidth(i,i==3?14000:8500);}
            importSheet.setDefaultColumnStyle(0,text);importSheet.setDefaultRowHeightInPoints(19);headers.setHeightInPoints(32);importSheet.createFreezePane(0,1);
            Sheet guide=book.createSheet("HUONG_DAN");
            guide.createRow(0).createCell(0).setCellValue("Nhập dữ liệu ở sheet IMPORT / Enter data in IMPORT sheet");
            guide.createRow(1).createCell(0).setCellValue("Mã nhân viên và họ tên bắt buộc / Employee code and full name required");
            guide.createRow(2).createCell(0).setCellValue("Mật khẩu trống dùng Pass@1234; tài khoản USER bị khóa đăng nhập, chưa cấp quyền / Blank password uses Pass@1234; USER login is locked and access is not granted");
            guide.createRow(3).createCell(0).setCellValue("Chỉ nhập mã phòng ban đang hoạt động / Use an active department code");
            guide.setColumnWidth(0,25000);
            book.write(out);return out.toByteArray();
        } catch(IOException cause) { throw new IllegalStateException("Account import template unavailable",cause); }
    }
    public Result preview(MultipartFile file) { return process(file,false); }
    public Result confirm(MultipartFile file) { return process(file,true); }

    private Result process(MultipartFile file, boolean confirm) {
        List<Candidate> candidates=read(file);
        Set<String> codes=new HashSet<>(),emails=new HashSet<>();
        List<List<String>> checked=new ArrayList<>();
        for(var candidate:candidates) {
            var errors=new ArrayList<>(candidate.parseErrors());
            errors.addAll(validate(candidate,codes,emails));
            checked.add(errors);
        }
        List<Item> rows=new ArrayList<>();
        int created=0;
        for(int index=0;index<candidates.size();index++) {
            var candidate=candidates.get(index);
            List<String> errors=checked.get(index);
            Long id=null;
            if(errors.isEmpty() && confirm) {
                try {
                    id=transactions.execute(status -> {
                        // Serialize with profile creation and correction before rechecking this row.
                        jdbc.execute("SELECT pg_advisory_xact_lock(7240319)");
                        var again=validate(candidate,new HashSet<>(),new HashSet<>());
                        if(!again.isEmpty()) throw new RowRejected(again);
                        Long department=departmentId(candidate.department());
                        UserEntity user=identities.importUser(new IdentityService.CreateUser(candidate.name(),candidate.code(),candidate.email(),department,candidate.password(),false));
                        return user.getUserId();
                    });
                    created++;
                } catch(RowRejected rejected) { errors.addAll(rejected.errors); }
                catch(AppException rejected) { errors.add(rejected.getCode()); }
                catch(RuntimeException rejected) { errors.add("ROW_CREATE_FAILED"); }
            }
            rows.add(new Item(candidate.row(),candidate.code(),candidate.name(),candidate.email(),candidate.department(),
                errors.isEmpty()?(confirm?"CREATED":"VALID"):"INVALID",List.copyOf(errors),id));
        }
        int invalid=(int)rows.stream().filter(row->!row.errors().isEmpty()).count();
        return new Result(List.copyOf(rows),rows.size()-invalid,invalid,created);
    }
    private static class RowRejected extends RuntimeException {
        final List<String> errors;
        RowRejected(List<String> errors) { this.errors=errors; }
    }
    private Long departmentId(String code) {
        if(code==null) return null;
        return jdbc.queryForObject("SELECT department_id FROM departments WHERE lower(code)=lower(?) AND is_active=true",Long.class,code);
    }
    private List<String> validate(Candidate row, Set<String> codes, Set<String> emails) {
        var errors=new ArrayList<String>();
        String code=row.code(),name=row.name(),email=row.email(),password=row.password(),department=row.department();
        if(code==null || code.length()>100) errors.add("EMPLOYEE_CODE_REQUIRED_OR_TOO_LONG");
        if(name==null || name.length()>255) errors.add("FULL_NAME_REQUIRED_OR_TOO_LONG");
        if(email!=null && (email.length()>255 || !EMAIL.matcher(email).matches())) errors.add("EMAIL_INVALID");
        if(password!=null && (password.length()<8 || password.length()>128 || password.getBytes(StandardCharsets.UTF_8).length>72)) errors.add("PASSWORD_INVALID");
        if(department!=null && (department.length()>50 || !Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM departments WHERE lower(code)=lower(?) AND is_active=true)",Boolean.class,department)))) errors.add("DEPARTMENT_NOT_FOUND");
        if(code!=null && emails.contains(code.toLowerCase(Locale.ROOT)) || email!=null && codes.contains(email.toLowerCase(Locale.ROOT))) errors.add("FILE_IDENTIFIER_COLLISION");
        if(code!=null && !codes.add(code.toLowerCase(Locale.ROOT))) errors.add("FILE_DUPLICATE_CODE");
        if(email!=null && !emails.add(email.toLowerCase(Locale.ROOT))) errors.add("FILE_DUPLICATE_EMAIL");
        if(code!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower(employee_code)=lower(?) OR lower(email)=lower(?))",Boolean.class,code,code))) errors.add("IDENTITY_DUPLICATE");
        if(email!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower(email)=lower(?) OR lower(employee_code)=lower(?))",Boolean.class,email,email))) errors.add("IDENTITY_DUPLICATE");
        return errors;
    }
    private List<Candidate> read(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>10_000_000 || file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new AppException(HttpStatus.BAD_REQUEST,"ACCOUNT_IMPORT_FILE_INVALID","Invalid Excel file");
        try(var book=new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet=book.getSheet("IMPORT");
            if(sheet==null || sheet.getRow(0)==null) throw new IllegalArgumentException("IMPORT sheet/header missing");
            DataFormatter formatter=new DataFormatter();
            for(int i=0;i<HEADERS.length;i++) if(!HEADERS[i].equals(text(sheet.getRow(0).getCell(i),formatter)))
                throw new IllegalArgumentException("Invalid IMPORT header");
            if(sheet.getRow(0).getLastCellNum()>5) throw new IllegalArgumentException("Unexpected IMPORT column");
            var result=new ArrayList<Candidate>();
            if(sheet.getLastRowNum()>1000) throw new IllegalArgumentException("Too many IMPORT rows");
            for(int index=1;index<=sheet.getLastRowNum();index++) {
                Row row=sheet.getRow(index);if(row==null) continue;
                if(row.getLastCellNum()>5) throw new IllegalArgumentException("Unexpected IMPORT column");
                String[] values=new String[5];var parseErrors=new ArrayList<String>();
                for(int col=0;col<5;col++) try { values[col]=col==3?password(row.getCell(col),formatter):text(row.getCell(col),formatter); }
                    catch(IllegalArgumentException invalid) { parseErrors.add("FORMULA_OR_CELL_ERROR"); }
                if(Arrays.stream(values).allMatch(Objects::isNull) && parseErrors.isEmpty()) continue;
                if(result.size()>=1000) throw new IllegalArgumentException("Too many IMPORT rows");
                result.add(new Candidate(index+1,values[0],values[1],values[2],values[3],values[4],List.copyOf(parseErrors)));
            }
            return result;
        } catch(Exception cause) {
            if(cause instanceof AppException error) throw error;
            throw new AppException(HttpStatus.BAD_REQUEST,"ACCOUNT_IMPORT_FILE_INVALID","Invalid IMPORT workbook");
        }
    }
    private String text(Cell cell,DataFormatter formatter) {
        if(cell==null || cell.getCellType()==CellType.BLANK) return null;
        if(cell.getCellType()==CellType.FORMULA || cell.getCellType()==CellType.ERROR) throw new IllegalArgumentException("Formula/error cell");
        String value=formatter.formatCellValue(cell).trim();
        return value.isEmpty()?null:value;
    }
    private String password(Cell cell,DataFormatter formatter) {
        if(cell==null || cell.getCellType()==CellType.BLANK) return null;
        if(cell.getCellType()==CellType.FORMULA || cell.getCellType()==CellType.ERROR) throw new IllegalArgumentException("Formula/error cell");
        String value=formatter.formatCellValue(cell);
        return value.isBlank()?null:value;
    }
}
