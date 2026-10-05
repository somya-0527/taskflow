package com.taskflow.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// NOTE: deliberately NO @Component here. See the explanation below.
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AuthenticatedUser user = jwtService.parseToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        user,                                                           // principal: who
                        null,                                                           // credentials: not needed anymore
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))); // authorities: what they can do
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // Bad or expired token: leave the user anonymous.
                // If the URL is protected, the entry point will answer 401.
            }
        }

        chain.doFilter(request, response);   // ALWAYS pass the request on to the next filter
    }
}