package com.company.itam.workflow.receiving.service;

import com.company.itam.asset.dto.request.*;
import com.company.itam.asset.service.AssetService;
import com.company.itam.common.exception.AppException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Validated, independent working copies; submitted snapshots never read mutable asset data. */
@Service
public class ReceivingLineService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final AssetService assets;
    public ReceivingLineService(JdbcTemplate jdbc, ObjectMapper mapper, Validator validator, AssetService assets) {
        this.jdbc=jdbc; this.mapper=mapper; this.validator=validator; this.assets=assets;
    }
    public ObjectNode current(Long id) {
        String json=jdbc.queryForObject("""
            SELECT jsonb_build_object('assetTag',a.asset_tag,'name',a.name,'typeId',a.type_id,
              'departmentId',a.department_id,'locationId',a.location_id,'supplierId',a.supplier_id,
              'poNumber',a.po_number,'purchaseDate',a.purchase_date,'purchaseCost',a.purchase_cost,
              'serialNumber',h.serial_number,'modelId',h.model_id,'conditionId',h.condition_id,
              'warrantyExpiration',h.warranty_expiration,'actualCpu',h.actual_cpu,'actualRam',h.actual_ram,
              'actualStorage',h.actual_storage,'actualGraphicsCard',h.actual_graphics_card,
              'license',CASE WHEN l.asset_id IS NULL THEN NULL ELSE jsonb_build_object(
                'softwareCatalogId',l.software_catalog_id,'assignmentTypeId',l.license_assignment_type_id,
                'termTypeId',l.license_term_type_id,'seatCount',l.seat_count,'expiryDate',l.expiry_date) END)::text
            FROM assets a LEFT JOIN asset_hardware_details h USING(asset_id)
            LEFT JOIN asset_license_details l USING(asset_id) WHERE a.asset_id=?
            """,String.class,id);
        return describe(id,read(json),false);
    }
    public ObjectNode read(String json) {
        try { return (ObjectNode)mapper.readTree(json); }
        catch (Exception ex) { throw new IllegalStateException("Invalid receiving data",ex); }
    }
    public CreateHardwareAssetRequest request(JsonNode line) {
        return mapper.convertValue(line.has("input")?line.get("input"):line,CreateHardwareAssetRequest.class);
    }
    public ObjectNode describe(Long id, JsonNode input, boolean complete) {
        var r=request(input);
        r.setAssignedToUserId(null); r.setStatusId(null);
        if (!validator.validate(r).isEmpty()) fail("VALIDATION_ERROR");
        ref("asset_types","type_id",r.getTypeId());
        String category=jdbc.queryForObject("SELECT c.code FROM asset_types t JOIN asset_categories c USING(category_id) WHERE t.type_id=?",String.class,r.getTypeId());
        ref("departments","department_id",r.getDepartmentId()); ref("locations","location_id",r.getLocationId());
        ref("suppliers","supplier_id",r.getSupplierId());
        boolean license="LICENSE".equals(category);
        if (license) {
            var l=r.getLicense();
            if (l==null || !validator.validate(l).isEmpty()) fail("LICENSE_DETAILS_REQUIRED");
            ref("software_catalog","software_catalog_id",l.softwareCatalogId());
            ref("license_assignment_types","license_assignment_type_id",l.assignmentTypeId()); ref("license_term_types","license_term_type_id",l.termTypeId());
            String term=jdbc.queryForObject("SELECT code FROM license_term_types WHERE license_term_type_id=?",String.class,l.termTypeId());
            if ("SUBSCRIPTION".equals(term) && l.expiryDate()==null) fail("LICENSE_EXPIRY_REQUIRED");
            if (r.getModelId()!=null || r.getConditionId()!=null || nonblank(r.getSerialNumber()) || r.getWarrantyExpiration()!=null
                || nonblank(r.getActualCpu()) || nonblank(r.getActualRam()) || nonblank(r.getActualStorage()) || nonblank(r.getActualGraphicsCard())) fail("LICENSE_HARDWARE_FIELDS");
            // Purchasing references and revisions never expose a license key.
            r.setLicense(new LicenseDetailsRequest(l.softwareCatalogId(),l.assignmentTypeId(),l.termTypeId(),l.seatCount(),null,l.expiryDate()));
        } else {
            if (r.getLicense()!=null) fail("LICENSE_DETAILS_REQUIRED");
            if (complete && r.getConditionId()==null) fail("IMPORT_CONDITION_REQUIRED");
            ref("asset_conditions","condition_id",r.getConditionId()); ref("models","model_id",r.getModelId());
            if (r.getModelId()!=null && !Objects.equals(r.getTypeId(),jdbc.queryForObject("SELECT type_id FROM models WHERE model_id=?",Long.class,r.getModelId()))) fail("MODEL_TYPE_MISMATCH");
        }
        r.setName(r.getName().trim());
        if (nonblank(r.getAssetTag())) r.setAssetTag(r.getAssetTag().trim());
        if (nonblank(r.getSerialNumber())) r.setSerialNumber(r.getSerialNumber().trim()); else r.setSerialNumber(null);
        if (id!=null && !Objects.equals(r.getTypeId(),jdbc.queryForObject("SELECT type_id FROM assets WHERE asset_id=?",Long.class,id))) fail("ASSET_WORKFLOW_REQUIRED");
        unique("assets","asset_tag",r.getAssetTag(),id,"DUPLICATE_ASSET_TAG");
        unique("asset_hardware_details","serial_number",r.getSerialNumber(),id,"DUPLICATE_SERIAL_NUMBER");
        ObjectNode result=mapper.createObjectNode();
        result.put("assetId",id); result.put("assetTag",r.getAssetTag()); result.put("name",r.getName());
        result.put("typeId",r.getTypeId()); result.put("category",category); result.set("input",mapper.valueToTree(r));
        ObjectNode labels=result.putObject("labels");
        label(labels,"type","asset_types","type_id",r.getTypeId()); label(labels,"condition","asset_conditions","condition_id",r.getConditionId());
        label(labels,"location","locations","location_id",r.getLocationId()); label(labels,"supplier","suppliers","supplier_id",r.getSupplierId());
        label(labels,"department","departments","department_id",r.getDepartmentId());
        if (license) {
            label(labels,"software","software_catalog","software_catalog_id",r.getLicense().softwareCatalogId());
            labels.put("assignment",jdbc.queryForObject("SELECT code FROM license_assignment_types WHERE license_assignment_type_id=?",String.class,r.getLicense().assignmentTypeId()));
            labels.put("term",jdbc.queryForObject("SELECT code FROM license_term_types WHERE license_term_type_id=?",String.class,r.getLicense().termTypeId()));
        }
        ObjectNode model=mapper.createObjectNode();
        if (r.getModelId()!=null) model=read(jdbc.queryForObject("SELECT to_jsonb(m)::text FROM models m WHERE model_id=?",String.class,r.getModelId()));
        result.set("model",model);
        ObjectNode effective=result.putObject("effectiveHardware");
        effective.put("cpu",nonblank(r.getActualCpu())?r.getActualCpu():model.path("default_cpu").asText(""));
        effective.put("ram",nonblank(r.getActualRam())?r.getActualRam():model.path("default_ram").asText(""));
        effective.put("storage",nonblank(r.getActualStorage())?r.getActualStorage():model.path("default_storage").asText(""));
        effective.put("graphics",nonblank(r.getActualGraphicsCard())?r.getActualGraphicsCard():model.path("default_graphics_card").asText(""));
        return result;
    }
    public void save(Long transactionId, Long assetId, ObjectNode line) {
        jdbc.update("UPDATE transaction_assets SET draft_data=CAST(? AS jsonb) WHERE transaction_id=? AND asset_id=?",line.toString(),transactionId,assetId);
    }
    public ObjectNode load(Long transactionId, Long assetId) {
        var rows=jdbc.queryForList("SELECT draft_data::text FROM transaction_assets WHERE transaction_id=? AND asset_id=?",String.class,transactionId,assetId);
        if(rows.isEmpty()) fail("TRANSACTION_REQUEST_INVALID");
        String data=rows.getFirst();
        return data==null?current(assetId):read(data);
    }
    public void apply(Long transactionId, Long assetId) {
        var line=describe(assetId,load(transactionId,assetId),true);
        var r=mapper.convertValue(line.get("input"),UpdateHardwareAssetRequest.class);
        assets.updateReceivingAsset(assetId,r);
        save(transactionId,assetId,line);
    }
    private void ref(String table,String column,Long id) {
        if(id!=null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE "+column+"=? AND is_active)",Boolean.class,id))) fail("IMPORT_REFERENCE_INVALID");
    }
    private void label(ObjectNode target,String key,String table,String column,Long id) {
        if(id!=null) target.put(key,jdbc.queryForObject("SELECT name FROM "+table+" WHERE "+column+"=?",String.class,id));
    }
    private void unique(String table,String column,String value,Long id,String code) {
        if(nonblank(value) && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE "+column+"=? AND (?::bigint IS NULL OR asset_id<>?))",Boolean.class,value,id,id))) fail(code);
    }
    private boolean nonblank(String value) { return value!=null && !value.isBlank(); }
    private void fail(String code) { throw new AppException(HttpStatus.CONFLICT,code,code); }
}
