package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
/**
 * This class is created by CDK init and customized later on.
 * @author Himanshu
 *
 */
public class InfraApp {
    public static void main(final String[] args) {
        App app = new App();

        new InfraStack(app, "LambdaCDKStack", StackProps.builder()
              
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())

                .build());

        app.synth();
    }
}

