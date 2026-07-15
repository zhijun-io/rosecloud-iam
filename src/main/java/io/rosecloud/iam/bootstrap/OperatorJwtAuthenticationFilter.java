package io.rosecloud.iam.bootstrap;

import io.rosecloud.iam.access.OperatorPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OperatorJwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtIssuer jwtIssuer;

  public OperatorJwtAuthenticationFilter(JwtIssuer jwtIssuer) {
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
      Optional<UUID> operatorId = jwtIssuer.verifyOperatorAccessToken(token);
      if (operatorId.isPresent()) {
        OperatorPrincipal principal = new OperatorPrincipal(operatorId.get());
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal, token, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("operatorId", operatorId.get());
      }
    }

    filterChain.doFilter(request, response);
  }
}
