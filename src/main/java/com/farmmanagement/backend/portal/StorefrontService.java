package com.farmmanagement.backend.portal;

import com.farmmanagement.backend.common.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

@Service
@Transactional
public class StorefrontService {
    private final StorefrontStore store;
    private final ObjectMapper mapper;
    public StorefrontService(StorefrontStore store,ObjectMapper mapper) { this.store=store;this.mapper=mapper; }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> catalog() { return store.catalog(); }
    public JsonNode checkout(JsonNode body) {
        Input.fields(body,"requestKey","buyerName","phone","email","address","note","items");
        UUID.fromString(Input.text(body,"requestKey",36));
        Input.text(body,"buyerName",100);
        Input.text(body,"address",500);
        if(!Input.text(body,"phone",20).matches("[+0-9 ()-]{7,20}")) throw new IllegalArgumentException();
        String email=Input.optionalText(body,"email",254);
        if(!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException();
        Input.optionalText(body,"note",500);
        var items=body.path("items");
        if(!items.isArray() || items.isEmpty() || items.size()>50) throw new IllegalArgumentException();
        var seen=new HashSet<Integer>();
        for(var item:items) {
            Input.fields(item,"listingId","quantity","version");
            if(!seen.add(Input.positiveInt(item,"listingId"))) throw new IllegalArgumentException();
            Input.positiveInt(item,"version");
            var quantity=item.path("quantity");
            if(!quantity.isNumber()) throw new IllegalArgumentException();
            BigDecimal amount=quantity.decimalValue();
            if(amount.signum()<=0 || amount.compareTo(new BigDecimal("999999999.999"))>0 || amount.stripTrailingZeros().scale()>3)
                throw new IllegalArgumentException();
        }
        return receipt(store.checkout(mapper.writeValueAsString(body)));
    }
    @Transactional(readOnly=true)
    public JsonNode receipt(UUID id) {
        String result=store.receipt(id);
        if(result==null) throw DomainException.missing();
        return mapper.readTree(result);
    }
}
