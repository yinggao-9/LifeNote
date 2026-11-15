package come.ying.lifenote.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtils {
    //create jwt
    public static String createJwt(String secretKey, long ttl, Map<String,Object> claims){
        //1.set expiration time
        long time = System.currentTimeMillis() + ttl;
        Date date = new Date(time);
        //2.encode secret time
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        //3.create jtw object
        String token = Jwts.builder()
                       .claims((claims))
                       .expiration(date)
                       .signWith(key)
                       .compact();
        return token;
    }
    //parse jwt
    public static Claims parseJwt(String secretKey,String token){
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
