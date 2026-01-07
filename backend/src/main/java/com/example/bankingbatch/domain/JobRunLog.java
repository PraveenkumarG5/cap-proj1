package com.example.bankingbatch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Tracks job run start/end/status for quick operational visibility.
@Entity
@Table(name = "job_run_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "details", columnDefinition = "CLOB")
    private String details;
}


