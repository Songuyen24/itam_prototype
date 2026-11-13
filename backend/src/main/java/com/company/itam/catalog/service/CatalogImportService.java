package com.company.itam.catalog.service;

import com.company.itam.catalog.dto.*;
import com.company.itam.department.dto.DepartmentRequest;
import com.company.itam.department.service.DepartmentService;
import com.company.itam.location.dto.LocationRequest;
import com.company.itam.location.service.LocationService;
import com.company.itam.supplier.dto.SupplierRequest;
import com.company.itam.supplier.service.SupplierService;
import com.company.itam.common.exception.AppException;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class CatalogImportService {
    private static final Map<String,List<String>> COLUMNS=Map.of(
        "departments",List.of("code / Mã", "name / Tên"),
        "locations",List.of("code / Mã", "name / Tên", "address / Địa chỉ"),
        "suppliers",List.of("code / Mã", "name / Tên", "taxCode / Mã số thuế", "address / Địa chỉ", "phone / Điện thoại", "email / Email"),
        "types",List.of("code / Mã", "name / Tên", "categoryCode / Mã nhóm tài sản"),
        "models",List.of("name / Tên", "brand / Hãng", "typeCode / Mã loại tài sản", "defaultCpu / CPU mặc định", "defaultRam / RAM mặc định", "defaultStorage / Lưu trữ mặc định", "defaultGraphicsCard / Đồ họa mặc định"),
        "software",List.of("name / Tên", "manufacturer / Nhà sản xuất", "version / Phiên bản"));
    private static final Map<String,String> TABLE=Map.of("departments","departments","locations","locations","suppliers","suppliers","types","asset_types","models","models","software","software_catalog");
    public record RowResult(int row, List<String> values, String status, List<String> errors) {}
    public record Result(List<String> columns, List<RowResult> rows, int valid, int invalid, int created) {}
    private record Candidate(int row, List<String> values, List<String> errors) { String get(int i) { return i<values.size()?values.get(i):null; } }
    private static final class Rejected extends RuntimeException { final List<String> errors; Rejected(List<String> errors){this.errors=errors;} }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DepartmentService departments;
    private final LocationService locations;
    private final SupplierService suppliers;
    private final CatalogService catalogs;
    private final AuditLogRepository audit;
    private final UserRepository users;
    public CatalogImportService(JdbcTemplate jdbc,PlatformTransactionManager manager,DepartmentService departments,LocationService locations,SupplierService suppliers,CatalogService catalogs,AuditLogRepository audit,UserRepository users) {
        this.jdbc=jdbc;this.transactions=new TransactionTemplate(manager);this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.departments=departments;this.locations=locations;this.suppliers=suppliers;this.catalogs=catalogs;this.audit=audit;this.users=users;
    }
    public byte[] template(String kind) {
        var columns=columns(kind);
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("IMPORT");var header=sheet.createRow(0);
            var text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));
            for(int i=0;i<columns.size();i++){header.createCell(i,CellType.STRING).setCellValue(columns.get(i));sheet.setDefaultColumnStyle(i,text);sheet.setColumnWidth(i,8000);}
            sheet.createFreezePane(0,1);
            var guide=book.createSheet("HUONG_DAN");guide.createRow(0).createCell(0).setCellValue("Nhập vào IMPORT; chỉ tạo mới. / Fill IMPORT; create new records only.");
            guide.createRow(1).createCell(0).setCellValue("Dùng mã nhóm/loại đang hoạt động; không dùng ID. / Use active category/type codes, not IDs.");
            guide.setColumnWidth(0,25000);book.write(out);return out.toByteArray();
        }catch(IOException cause){throw new IllegalStateException("Catalog template unavailable",cause);}
    }
    public Result preview(String kind,MultipartFile file){return process(kind,file,false);}
    public Result confirm(String kind,MultipartFile file){return process(kind,file,true);}
    private Result process(String kind,MultipartFile file,boolean confirm){
        var candidates=read(kind,file);var seen=new HashSet<String>();var checked=new ArrayList<List<String>>();
        for(var row:candidates){var errors=new ArrayList<>(row.errors());errors.addAll(validate(kind,row,seen));checked.add(errors);}
        var rows=new ArrayList<RowResult>();int created=0;
        for(int i=0;i<candidates.size();i++){
            var row=candidates.get(i);var errors=checked.get(i);
            if(confirm&&errors.isEmpty())try{
                transactions.execute(status->{
                    jdbc.execute("SELECT pg_advisory_xact_lock(7240320)");
                    var fresh=validate(kind,row,new HashSet<>());
                    if(!fresh.isEmpty())throw new Rejected(fresh);
                    Long id=create(kind,row);
                    var auth=SecurityContextHolder.getContext().getAuthentication();
                    var actor=users.findByPrincipal(auth==null?null:auth.getName()).orElseThrow();
                    var event=new AuditLogEntity();event.setEntityType("MASTER_DATA");event.setEntityId(id);event.setActor(actor);event.setAction("CATALOG_IMPORTED");
                    var data=new LinkedHashMap<String,Object>();data.put("kind",kind);data.put("key",row.get(0));data.put("name",row.get("models".equals(kind)||"software".equals(kind)?0:1));
                    if("types".equals(kind)||"models".equals(kind))data.put("referenceCode",row.get(2));
                    event.setNewData(data);audit.save(event);return null;
                });created++;
            }catch(Rejected rejected){errors.addAll(rejected.errors);}
             catch(RuntimeException rejected){errors.add("CREATE_FAILED");}
            rows.add(new RowResult(row.row(),List.copyOf(row.values().stream().map(value->value==null?"":value).toList()),errors.isEmpty()?(confirm?"CREATED":"VALID"):"INVALID",List.copyOf(errors)));
        }
        int invalid=(int)rows.stream().filter(row->!row.errors().isEmpty()).count();
        return new Result(columns(kind),List.copyOf(rows),rows.size()-invalid,invalid,created);
    }
    private List<String> validate(String kind,Candidate row,Set<String> seen){
        var errors=new ArrayList<String>();var values=row.values();
        int[] required=switch(kind){case "models","software","departments","locations","suppliers"->new int[]{0,1};case "types"->new int[]{0,1,2};default->new int[0];};
        if("models".equals(kind))required=new int[]{0,1,2};
        for(int index:required)if(row.get(index)==null)errors.add("REQUIRED_"+index);
        int[] limits=switch(kind){case "departments"->new int[]{50,255};case "locations"->new int[]{50,255,500};case "suppliers"->new int[]{50,255,100,500,50,255};case "types"->new int[]{50,255,50};case "models"->new int[]{255,255,50,255,100,100,255};default->new int[]{255,255,100};};
        for(int index=0;index<values.size();index++)if(values.get(index)!=null&&values.get(index).length()>limits[index])errors.add("TOO_LONG_"+index);
        if("suppliers".equals(kind)&&row.get(5)!=null&&!row.get(5).matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))errors.add("EMAIL_INVALID");
        String identity=switch(kind){case "models"->String.join("|",Objects.toString(row.get(2),""),Objects.toString(row.get(1),""),Objects.toString(row.get(0),""));case "software"->String.join("|",Objects.toString(row.get(1),""),Objects.toString(row.get(0),""),Objects.toString(row.get(2),""));default->Objects.toString(row.get(0),"");};
        if(!seen.add(identity.toLowerCase(Locale.ROOT)))errors.add("FILE_DUPLICATE");
        if(!identity.isBlank()&&exists(kind,row))errors.add("ALREADY_EXISTS");
        if("types".equals(kind)&&row.get(2)!=null&&!referenceExists("asset_categories",row.get(2)))errors.add("REFERENCE_NOT_FOUND");
        if("models".equals(kind)&&row.get(2)!=null&&!referenceExists("asset_types",row.get(2)))errors.add("REFERENCE_NOT_FOUND");
        return errors;
    }
    private boolean referenceExists(String table,String code){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE lower(code)=lower(?) AND is_active=true)",Boolean.class,code));}
    private boolean exists(String kind,Candidate row){
        if("models".equals(kind))return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM models m JOIN asset_types t ON t.type_id=m.type_id WHERE lower(t.code)=lower(?) AND lower(m.brand)=lower(?) AND lower(m.name)=lower(?))",Boolean.class,row.get(2),row.get(1),row.get(0)));
        if("software".equals(kind))return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM software_catalog WHERE lower(manufacturer)=lower(?) AND lower(name)=lower(?) AND lower(coalesce(version,''))=lower(?))",Boolean.class,row.get(1),row.get(0),Objects.toString(row.get(2),"")));
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+TABLE.get(kind)+" WHERE lower(code)=lower(?))",Boolean.class,row.get(0)));
    }
    private Long create(String kind,Candidate row){
        return switch(kind){
            case "departments"->departments.createDepartment(new DepartmentRequest(row.get(0),row.get(1),true)).getDepartmentId();
            case "locations"->locations.createLocation(new LocationRequest(row.get(0),row.get(1),row.get(2),true)).getLocationId();
            case "suppliers"->{var request=new SupplierRequest();request.setCode(row.get(0));request.setName(row.get(1));request.setTaxCode(row.get(2));request.setAddress(row.get(3));request.setPhone(row.get(4));request.setEmail(row.get(5));yield suppliers.createSupplier(request).getSupplierId();}
            case "types"->{Long id=jdbc.queryForObject("SELECT category_id FROM asset_categories WHERE lower(code)=lower(?) AND is_active=true",Long.class,row.get(2));yield catalogs.createAssetType(new AssetTypeRequest(row.get(0),id,row.get(1),true)).getTypeId();}
            case "models"->{Long id=jdbc.queryForObject("SELECT type_id FROM asset_types WHERE lower(code)=lower(?) AND is_active=true",Long.class,row.get(2));var request=new ModelRequest();request.setName(row.get(0));request.setBrand(row.get(1));request.setTypeId(id);request.setDefaultCpu(row.get(3));request.setDefaultRam(row.get(4));request.setDefaultStorage(row.get(5));request.setDefaultGraphicsCard(row.get(6));yield catalogs.createModel(request).getModelId();}
            case "software"->catalogs.createSoftwareCatalog(new SoftwareCatalogRequest(row.get(0),row.get(1),row.get(2),true)).getSoftwareCatalogId();
            default->throw invalid();
        };
    }
    private List<Candidate> read(String kind,MultipartFile file){
        var columns=columns(kind);
        if(file==null||file.isEmpty()||file.getSize()>10_000_000||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw invalid();
        try(var book=new XSSFWorkbook(file.getInputStream())){
            var sheet=book.getSheet("IMPORT");if(sheet==null||sheet.getRow(0)==null||sheet.getLastRowNum()>1000)throw invalid();
            var formatter=new DataFormatter();var header=sheet.getRow(0);
            if(header.getLastCellNum()>columns.size())throw invalid();
            for(int index=0;index<columns.size();index++)if(header.getCell(index)==null||header.getCell(index).getCellType()==CellType.FORMULA||!columns.get(index).equals(formatter.formatCellValue(header.getCell(index)).trim()))throw invalid();
            var result=new ArrayList<Candidate>();
            for(int index=1;index<=sheet.getLastRowNum();index++){
                var row=sheet.getRow(index);if(row==null)continue;
                if(row.getLastCellNum()>columns.size())throw invalid();
                var values=new ArrayList<String>();var errors=new ArrayList<String>();
                for(int col=0;col<columns.size();col++){
                    var cell=row.getCell(col);
                    if(cell==null||cell.getCellType()==CellType.BLANK){values.add(null);continue;}
                    if(cell.getCellType()==CellType.FORMULA||cell.getCellType()==CellType.ERROR){values.add(null);errors.add("FORMULA_OR_CELL_ERROR");continue;}
                    var value=formatter.formatCellValue(cell).trim();values.add(value.isEmpty()?null:value);
                }
                if(values.stream().allMatch(Objects::isNull)&&errors.isEmpty())continue;
                result.add(new Candidate(index+1,values,errors));
            }
            return result;
        }catch(Exception cause){throw invalid();}
    }
    private List<String> columns(String kind){var result=COLUMNS.get(kind);if(result==null)throw invalid();return result;}
    private AppException invalid(){return new AppException(HttpStatus.BAD_REQUEST,"CATALOG_IMPORT_INVALID","Invalid catalog import workbook");}
}
