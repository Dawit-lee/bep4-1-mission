package com.back.boundedContext.payout.in;

import com.back.boundedContext.payout.app.PayoutFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PayoutCollectItemsBatchJobConfig {
    private static final int CHUNK_SIZE = 10;

    private final PayoutFacade payoutFacade;

    public PayoutCollectItemsBatchJobConfig(PayoutFacade payoutFacade) {
        this.payoutFacade = payoutFacade;
    }

    @Bean
    public Job payoutCollectItemsJob(
            JobRepository jobRepository,
            Step payoutCollectItemsStep
    ) {
        return new JobBuilder("payoutCollectItemsJob", jobRepository)
                .start(payoutCollectItemsStep)
                .build();
    }

    @Bean
    public Step payoutCollectItemsStep(JobRepository jobRepository) {
        return new StepBuilder("payoutCollectItemsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    int processedCount = payoutFacade.collectPayoutItemsMore(CHUNK_SIZE).getData();

                    if (processedCount == 0) {
                        return RepeatStatus.FINISHED;
                    }

                    contribution.incrementWriteCount(processedCount);

                    return RepeatStatus.CONTINUABLE;
                })
                .build();
    }
}