package com.example.im.security;
import io.jsonwebtoken.Claims; import io.jsonwebtoken.Jwts; import io.jsonwebtoken.security.Keys; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import javax.crypto.SecretKey; import java.nio.charset.StandardCharsets; import java.util.Date; import java.util.UUID;
@Service public class JwtService {
 private final SecretKey key; private final long accessExpiration; private final long refreshExpiration; private final String issuer;
 public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.access-token-expiration}") long accessExpiration,@Value("${app.jwt.refresh-token-expiration}") long refreshExpiration,@Value("${app.jwt.issuer}") String issuer){key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.accessExpiration=accessExpiration;this.refreshExpiration=refreshExpiration;this.issuer=issuer;}
 public String access(Long id,String username){return create(id,username,"ACCESS",accessExpiration);}
 public String refresh(Long id,String username){return create(id,username,"REFRESH",refreshExpiration);}
 private String create(Long id,String username,String type,long ttl){Date now=new Date();return Jwts.builder().id(UUID.randomUUID().toString()).subject(username).claim("userId",id).claim("tokenType",type).issuer(issuer).issuedAt(now).expiration(new Date(now.getTime()+ttl)).signWith(key).compact();}
 public Claims parse(String token){return Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token).getPayload();}
 public boolean isType(String token,String type){return type.equals(parse(token).get("tokenType",String.class));}
 public long accessSeconds(){return accessExpiration/1000;}
}
