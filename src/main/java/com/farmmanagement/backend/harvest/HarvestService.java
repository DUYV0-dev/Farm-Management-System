package com.farmmanagement.backend.harvest;

import com.farmmanagement.backend.common.DomainException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HarvestService {
    private final HarvestStore store;
    public HarvestService(HarvestStore store) { this.store=store; }
    private void scope(int actor,int farm,boolean write) {
        if(farm<=0) throw new IllegalArgumentException();
        if(!store.allowed(actor,farm,write)) throw DomainException.missing();
    }
    private HarvestStore.Season season(int farm,int id) { return store.season(farm,id).orElseThrow(DomainException::missing); }
    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> farms(int actor) { return store.farms(actor); }
    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly=true)
    public List<HarvestStore.Season> seasons(int actor,int farm) { scope(actor,farm,false);return store.seasons(farm); }
    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> units() { return store.units(); }
    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> history(int actor,int farm,Integer seasonId,LocalDate from,LocalDate to,int page,int size) {
        scope(actor,farm,false);
        if(page<0 || page>100000 || size<1 || size>50 || (from!=null && to!=null && from.isAfter(to))) throw new IllegalArgumentException();
        if(seasonId!=null) season(farm,seasonId);
        return store.history(farm,seasonId,from,to,page,size);
    }
    @PreAuthorize("hasAuthority('harvests:read')")
    @Transactional(readOnly=true)
    public HarvestStore.Harvest get(int actor,int farm,int id) { scope(actor,farm,false);return store.get(farm,id).orElseThrow(DomainException::missing); }
    @PreAuthorize("hasAuthority('harvests:create')")
    public HarvestStore.Harvest create(int actor,int farm,int seasonId,LocalDate date,BigDecimal quantity,String quality,String requestId) {
        store.lock();scope(actor,farm,true);
        var season=season(farm,seasonId);
        validate(season,date,quantity,quality);
        int id=store.create(actor,seasonId,date,quantity,quality);
        store.audit(actor,"HARVEST",id,"CREATE",requestId);
        return store.get(farm,id).orElseThrow(DomainException::missing);
    }
    static void validate(HarvestStore.Season season,LocalDate date,BigDecimal quantity,String quality) {
        if(date==null || quantity==null || quantity.signum()<=0 || quantity.stripTrailingZeros().scale()>3
            || quantity.compareTo(new BigDecimal("9999999999999.999"))>0 || (quality!=null && quality.length()>50)) throw new IllegalArgumentException();
        if(!"IN_PROGRESS".equals(season.status()) || !"CONFIRMED".equals(season.recordStatus()))
            throw DomainException.conflict("Chỉ ghi thu hoạch cho mùa vụ đang thực hiện và đã xác nhận.");
        if(season.start()==null || season.end()==null || season.unitId()==null)
            throw DomainException.conflict("Mùa vụ chưa có đủ ngày và đơn vị sản lượng. Hãy cấu hình trước.");
        if(date.isBefore(season.start()) || date.isAfter(season.end()) || date.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw DomainException.conflict("Ngày thu hoạch phải trong mùa vụ và không vượt ngày hiện tại.");
    }
    @PreAuthorize("hasAuthority('crops:update') and hasAuthority('harvests:create')")
    public HarvestStore.Season configure(int actor,int farm,int id,String name,LocalDate start,LocalDate end,int unitId,String requestId) {
        store.lock();scope(actor,farm,true);var s=season(farm,id);
        if(!Set.of("PLANNED","IN_PROGRESS").contains(s.status())) throw DomainException.conflict("Mùa vụ đã kết thúc.");
        if(store.hasHarvest(id)) throw DomainException.conflict("Không đổi ngày hoặc đơn vị khi đã có thu hoạch.");
        if(name==null || name.isBlank() || name.length()>100 || start==null || end==null || !end.isAfter(start) || !store.unitExists(unitId)) throw new IllegalArgumentException();
        store.configure(id,name.trim(),start,end,unitId);store.audit(actor,"CROP_SEASON",id,"UPDATE",requestId);
        return season(farm,id);
    }
}
