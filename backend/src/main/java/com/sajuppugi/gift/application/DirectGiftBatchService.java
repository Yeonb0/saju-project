package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.DirectPaymentBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-priced mixed gift checkout; no link or recipient content is made here. */
@Service public class DirectGiftBatchService {
 private final JdbcTemplate jdbc; private final Clock clock;
 public DirectGiftBatchService(JdbcTemplate jdbc, Clock clock){this.jdbc=jdbc;this.clock=clock;}
 @Transactional(timeout=5) public DirectPaymentBatch create(UUID user,List<Option> recipients,String key){
  if(user==null||recipients==null||recipients.isEmpty()||key==null||key.isBlank()||key.length()>512)throw new ApiException(ErrorCode.INVALID_REQUEST);
  String kh=hash(key); var old=jdbc.query("SELECT * FROM direct_payment_batches WHERE buyer_user_id=? AND key_hash=?",(rs,n)->new DirectPaymentBatch(rs.getObject("id",UUID.class),rs.getObject("buyer_user_id",UUID.class),rs.getObject("quote_id",UUID.class),rs.getString("product_code"),rs.getString("product_name"),rs.getInt("unit_amount_krw"),rs.getInt("quantity"),rs.getInt("total_amount_krw"),DirectPaymentBatch.Status.valueOf(rs.getString("status")),rs.getTimestamp("created_at").toInstant()),user,kh);
  if(!old.isEmpty()) return old.getFirst();
  int total=recipients.stream().mapToInt(Option::amount).sum(); if(total>=50000)throw new ApiException(ErrorCode.PAYMENT_BATCH_LIMIT_EXCEEDED);
  Instant now=clock.instant(); UUID id=UUID.randomUUID(); var batch=new DirectPaymentBatch(id,user,null,"GIFT_SUNEUNG_BATCH","수능 선물",total,1,total,DirectPaymentBatch.Status.PAYMENT_PENDING,now);
  jdbc.update("INSERT INTO direct_payment_batches(id,buyer_user_id,quote_id,product_code,product_name,unit_amount_krw,quantity,total_amount_krw,key_hash,status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,user,null,batch.productCode(),batch.productName(),total,1,total,kh,batch.status().name(),java.sql.Timestamp.from(now));
  jdbc.update("INSERT INTO direct_gift_batches(payment_batch_id,buyer_user_id,status,recipient_count,created_at) VALUES (?,?, 'PAYMENT_PENDING',?,?)",id,user,recipients.size(),java.sql.Timestamp.from(now));
  for(int i=0;i<recipients.size();i++){Option o=recipients.get(i);jdbc.update("INSERT INTO direct_gift_batch_recipients(id,payment_batch_id,recipient_index,option,amount_krw,state,created_at) VALUES (?,?,?,?,?,'PENDING_PAYMENT',?)",UUID.randomUUID(),id,i,o.name(),o.amount(),java.sql.Timestamp.from(now));}
  return batch;
 }
 private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public enum Option { TALISMAN_ONLY(1000), READING_WITH_TALISMAN(3000); final int amount; Option(int a){amount=a;} public int amount(){return amount;} }
}
