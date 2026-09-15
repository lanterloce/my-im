package com.example.im.auth.dto; import java.util.Map; public record AuthResponse(String accessToken,String refreshToken,String tokenType,long expiresIn,Map<String,Object> user){}
