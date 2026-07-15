package io.rosecloud.iam.bootstrap;

import io.rosecloud.iam.access.OperatorPrincipal;
import io.rosecloud.iam.access.TenantPrincipal;
import io.rosecloud.iam.access.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessJwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtIssuer jwtIssuer;

  public AccessJwtAuthenticationFilter(JwtIssuer jwtIssuer) {
    this.jwtIssuer = jwtIssuer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization != null
        && authorization.startsWith("Bearer ")
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      String token = authorization.substring("Bearer ".length()).trim();
      jwtIssuer.verifyAccessToken(token).ifPresent(claims -> authenticate(request, token, claims));
    }

    filterChain.doFilter(request, response);
  }

  private void authenticate(
      HttpServletRequest request, String token, JwtIssuer.VerifiedAccessToken claims) {
    UsernamePasswordAuthenticationToken authentication;
    switch (claims.typ()) {
      case "operator" -> {
        OperatorPrincipal principal = new OperatorPrincipal(claims.subjectId());
        authentication =
            new UsernamePasswordAuthenticationToken(
                principal, token, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        request.setAttribute("operatorId", claims.subjectId());
      }
      case "user" -> {
        UserPrincipal principal = new UserPrincipal(claims.subjectId());
        authentication =
            new UsernamePasswordAuthenticationToken(
                principal, token, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        request.setAttribute("userId", claims.subjectId());
      }
      case "tenant" -> {
        TenantPrincipal principal =
            new TenantPrincipal(
                claims.subjectId(), claims.tenantId(), claims.membershipId(), claims.permissions());
        authentication =
            new UsernamePasswordAuthenticationToken(
                principal, token, List.of(new SimpleGrantedAuthority("ROLE_TENANT")));
        request.setAttribute("userId", claims.subjectId());
        request.setAttribute("tenantId", claims.tenantId());
        request.setAttribute("membershipId", claims.membershipId());
      }
      default -> {
        return;
      }
    }

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
