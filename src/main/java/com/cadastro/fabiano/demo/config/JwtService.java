package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(User user) {

        return Jwts.builder()

                // usuario do token
                .setSubject(user.getUsername())

                // claims extras
                .claim("role", user.getRole().name())

                .setIssuedAt(new Date())

                .setExpiration(new Date(System.currentTimeMillis() + 86400000))

                .signWith(getKey(), SignatureAlgorithm.HS256)

                .compact();
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

}