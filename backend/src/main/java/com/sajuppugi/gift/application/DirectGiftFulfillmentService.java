package com.sajuppugi.gift.application;
import com.sajuppugi.common.api.*; import com.sajuppugi.common.idempotency.IdempotencyKey; import com.sajuppugi.gift.domain.GiftLifetimePolicy; import com.sajuppugi.gift.infrastructure.GiftPayloadCipher; import com.sajuppugi.payment.application.DirectPaymentConsumptionService; import java.nio.charset.StandardCharsets; import java.security.*; import java.sql.Timestamp; import java.time.Clock; import java.time.Instant; import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
/** Issues exactly one opaque credential per paid recipient. Raw credentials never enter logs or persistence. */
@Service @ConditionalOnProperty(name="app.gift.purchases-enabled",havingValue="true") public class DirectGiftFulfillmentService { private final JdbcTemplate jdbc; private final DirectPaymentConsumptionService payments; private final GiftPayloadCipher cipher; private final GiftLinkService links; private final Clock clock; private final String base; private final SecureRandom random=new SecureRandom();
 public DirectGiftFulfillmentService(JdbcTemplate j,DirectPaymentConsumptionService p,GiftPayloadCipher x,GiftLinkService l,Clock c,@Value("${app.gift.frontend-base-url:http://localhost:3000}") String b){jdbc=j;payments=p;cipher=x;links=l;clock=c;base=b.replaceAll("/+$","");}
 @Transactional(timeout=10) public List<Link> fulfill(UUID buyer,UUID batch){
  var rows=jdbc.query("SELECT b.status FROM direct_gift_batches b WHERE b.payment_batch_id=? AND b.buyer_user_id=? FOR UPDATE",(rs,n)->rs.getString(1),batch,buyer); if(rows.isEmpty())throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
  payments.claimGiftBatch(buyer,batch);
  var recipients=jdbc.query("SELECT id,recipient_index,option,token_hash,order_id,gift_id FROM direct_gift_batch_recipients WHERE payment_batch_id=? ORDER BY recipient_index FOR UPDATE",(rs,n)->new R(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class)),batch);
  Instant now=clock.instant(); List<Link> out=new ArrayList<>(); for(R r:recipients){ if(r.hash()!=null){out.add(new Link(r.index(),r.id(),r.option(),null,true));continue;} String raw=token(); activateRecipient(buyer,batch,r,raw,now); out.add(new Link(r.index(),r.id(),r.option(),base+"/g#token="+raw,false)); }
  jdbc.update("UPDATE direct_gift_batches SET status='FULFILLED',fulfilled_at=? WHERE payment_batch_id=?",Timestamp.from(clock.instant()),batch); return List.copyOf(out);
 }
 @Transactional(readOnly=true,timeout=5) public PublicGift access(String raw){
  if(raw==null||raw.isBlank()||raw.length()>200)throw new ApiException(ErrorCode.INVALID_REQUEST);
  var rows=jdbc.query("SELECT id,option,state FROM direct_gift_batch_recipients WHERE token_hash=?",(rs,n)->new P(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),hash(raw));
  P p=rows.stream().findFirst().orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND)); return new PublicGift(p.id(),p.option(),p.state());
 }
 @Transactional(timeout=5) public Link reissue(UUID buyer,UUID recipient,IdempotencyKey key){
  var rows=jdbc.query("SELECT r.payment_batch_id,r.recipient_index,r.option,r.order_id FROM direct_gift_batch_recipients r JOIN direct_gift_batches b ON b.payment_batch_id=r.payment_batch_id WHERE r.id=? AND b.buyer_user_id=? FOR UPDATE",(rs,n)->new X(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),rs.getObject(4,UUID.class)),recipient,buyer);
  X x=rows.stream().findFirst().orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND)); if(x.order()==null)throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
  GiftLinkService.Link link=links.reissue(buyer,x.order(),key); if(link.shareUrl()!=null)jdbc.update("UPDATE direct_gift_batch_recipients SET token_hash=? WHERE id=?",hash(link.shareUrl().substring(link.shareUrl().indexOf("#token=")+7)),recipient);
  return new Link(x.index(),recipient,x.option(),link.shareUrl(),link.reused());
 }
 private void activateRecipient(UUID buyer, UUID batch, R recipient, String raw, Instant now){
  UUID order=UUID.randomUUID(), gift=UUID.randomUUID(); String product=product(recipient.option()); String tokenHash=hash(raw);
  String payload=cipher.encrypt("{\"recipientName\":\"수능 선물\",\"senderName\":\"선물 보낸 사람\",\"message\":\"\"}",buyer+"|"+order);
  jdbc.update("INSERT INTO gift_order_drafts(id,user_id,quote_id,request_hash,product_code,product_name,shell_amount,recipient_phone_masked,private_payload,status,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,'CREATED',?,?)",order,buyer,null,hash("direct-gift|"+batch+"|"+recipient.id()),product,productName(recipient.option()),0,null,payload,Timestamp.from(now),Timestamp.from(GiftLifetimePolicy.unusedExpiresAt(now)));
  jdbc.update("INSERT INTO gift_purchase_work(order_id,status,next_attempt_at,direct_payment_batch_id,approved_at,gift_id,access_expires_at) VALUES (?,'ACTIVE',?,?,?,?,?)",order,Timestamp.from(now),batch,Timestamp.from(now),gift,Timestamp.from(GiftLifetimePolicy.unusedExpiresAt(now)));
  jdbc.update("INSERT INTO gift_purchase_tokens(token_hash,order_id,issued_at,reveal_until) VALUES (?,?,?,?)",tokenHash,order,Timestamp.from(now),Timestamp.from(now));
  jdbc.update("UPDATE direct_gift_batch_recipients SET token_hash=?,order_id=?,gift_id=?,state='LINK_ACTIVE' WHERE id=? AND token_hash IS NULL",tokenHash,order,gift,recipient.id());
 }
 private String product(String option){return "TALISMAN_ONLY".equals(option)?"GIFT_SUNEUNG_TALISMAN_ONLY":"GIFT_SUNEUNG_READING_WITH_TALISMAN";} private String productName(String option){return "TALISMAN_ONLY".equals(option)?"수능 선물 부적":"수능 선물 사주+부적";}
 private String token(){byte[] v=new byte[32];random.nextBytes(v);return Base64.getUrlEncoder().withoutPadding().encodeToString(v);} private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private record R(UUID id,int index,String option,String hash,UUID order,UUID gift){} private record P(UUID id,String option,String state){} private record X(UUID batch,int index,String option,UUID order){} public record Link(int recipientIndex,UUID recipientId,String option,String shareUrl,boolean reused){@Override public String toString(){return "DirectGiftLink[REDACTED]";}} public record PublicGift(UUID recipientId,String option,String status){}
}
