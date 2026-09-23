package com.aparcar.api.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StripPortFromXffFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("X-Forwarded-For".equalsIgnoreCase(name)) {
                    String xff = super.getHeader(name);
                    if (xff != null && !xff.isBlank()) {
                        String client = xff.split(",")[0].trim();
                        return stripPort(client);
                    }
                }
                return super.getHeader(name);
            }

            @Override
            public String getRemoteAddr() {
                return stripPort(super.getRemoteAddr());
            }

            private String stripPort(String address) {
                if (address == null) return null;
                address = address.trim();

                if (address.startsWith("[") && address.endsWith("]:")) {
                    return address.substring(1, address.indexOf("]:"));
                }

                long colonCount = address.chars().filter(ch -> ch == ':').count();
                if (colonCount == 1) {
                    return address.substring(0, address.indexOf(':'));
                }

                return address;
            }
        };

        filterChain.doFilter(wrapped, response);
    }
}
