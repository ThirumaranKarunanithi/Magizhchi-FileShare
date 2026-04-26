package com.magizhchi.share.security;

import com.magizhchi.share.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository   userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest  req,
                                    HttpServletResponse res,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String token = resolveToken(req);

        if (StringUtils.hasText(token) && jwtTokenProvider.validate(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            userRepository.findById(userId).ifPresent(user -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, Collections.emptyList());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        chain.doFilter(req, res);
    }

    private String resolveToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            return header.substring(7);
        return null;
    }
}
