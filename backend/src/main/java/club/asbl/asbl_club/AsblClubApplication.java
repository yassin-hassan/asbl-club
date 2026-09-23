package club.asbl.asbl_club;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AsblClubApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsblClubApplication.class, args);
	}

}
