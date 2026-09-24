package club.asbl.asbl_club.auth;

import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
