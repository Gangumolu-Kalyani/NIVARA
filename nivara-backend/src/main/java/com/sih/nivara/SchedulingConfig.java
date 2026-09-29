package com.sih.nivara;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods, such as the reminder scheduler. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
