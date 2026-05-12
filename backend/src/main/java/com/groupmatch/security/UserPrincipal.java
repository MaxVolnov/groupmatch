package com.groupmatch.security;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import com.groupmatch.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Адаптер между domain-объектом {@link User} и интерфейсом {@link UserDetails}.
 *
 * Хранит минимум данных, необходимых Spring Security и JWT-фильтру:
 * идентификатор, email, хэш пароля, системную роль (USER/ADMIN) и план.
 * Дополнительная загрузка пользователя из БД в каждом запросе не требуется —
 * JwtAuthenticationFilter создаёт UserPrincipal напрямую из Claims JWT.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final Plan plan;
    private final boolean blocked;

    public UserPrincipal(User user) {
        this.id           = user.getId();
        this.email        = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role         = user.getRole();
        this.plan         = user.getPlan();
        this.blocked      = user.isBlocked();
    }

    /** Конструктор для восстановления из JWT Claims (без обращения к БД). */
    public UserPrincipal(UUID id, String email, Role role, Plan plan) {
        this.id           = id;
        this.email        = email;
        this.passwordHash = null;
        this.role         = role;
        this.plan         = plan;
        this.blocked      = false;
    }

    public UUID getId()   { return id; }
    public Role getRole() { return role; }
    public Plan getPlan() { return plan; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security ожидает "ROLE_USER" / "ROLE_ADMIN"
        return List.of(new SimpleGrantedAuthority(role.toAuthority()));
    }

    @Override public String getPassword()   { return passwordHash; }
    @Override public String getUsername()   { return email; }
    @Override public boolean isEnabled()    { return !blocked; }
    @Override public boolean isAccountNonLocked() { return !blocked; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
