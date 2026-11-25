package edu.bsu.worgit;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import edu.bsu.worgit.security.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
public class WorgitApplication {

    public static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(MANILA));
        SpringApplication.run(WorgitApplication.class, args);
    }

    /** One clock for the whole app (Asia/Manila); tests replace it. */
    @Bean
    Clock clock() {
        return Clock.system(MANILA);
    }
}

