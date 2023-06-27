package com.myorg;

import java.util.List;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.CfnDocumentationPart;
import software.amazon.awscdk.services.apigateway.CfnDocumentationPartProps;
import software.amazon.awscdk.services.apigateway.CfnDocumentationVersion;
import software.amazon.awscdk.services.apigateway.CfnDocumentationVersionProps;
import software.amazon.awscdk.services.apigateway.CognitoUserPoolsAuthorizer;
import software.amazon.awscdk.services.apigateway.CognitoUserPoolsAuthorizerProps;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.IResource;
import software.amazon.awscdk.services.apigateway.Integration;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.CfnUserPoolDomain;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.HttpMethod;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;
/**
 * This class creates AWS infrastructure using CDK.
 * Creates CogitoUserPool, APIGW(with Documentation), Lambda, Dynano DB and integrates them.
 * In real world it would also contain Public and Private SuSubnet along with NAT Gateway. 
 * @author Himanshu
 *
 */
public class InfraStack extends Stack {

    private static final String API_NAME = "Product_Service";
	private static final String API_STAGE = "demo";
	private static final String COGNITO_HOSTED_DOMAIN_UNIQUE = "demohimanshu2023";
	private static final String CALLBACK_URL = "https://www.lyngon.com";
	private static final int LAMBDA_TIMEOUT = 20;
	private static final int LAMBDA_MEMORY_MB = 256;
	private static final String LOCATION_FUNCTION_JAR = "../assets/function.jar";
	public static final String PRODUCT_TABLE_NAME = "product";

    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // DynamoDB table
        Table productsTable = createDynanoDb();

        // Create a new Cognito User Pool
        MethodOptions methodOptions = createCognitoUserPoolAuthorizer();

        Function getProductListFunction = new Function(this, "getlist-product-lambda",
                getFunctionProps("org.myorg.demo.lambda.GetProductList"));
        
        Function createFunction = new Function(this, "create-product-lambda",
                getFunctionProps("org.myorg.demo.lambda.AddProduct"));

        Function getFunction = new Function(this, "get-product-lambda",
                getFunctionProps("org.myorg.demo.lambda.GetProduct"));

        Function updateFunction = new Function(this, "update-product-lambda",
                getFunctionProps("org.myorg.demo.lambda.UpdateProduct"));

        Function deleteFunction = new Function(this, "delete-product-lambda",
                getFunctionProps("org.myorg.demo.lambda.DeleteProduct"));

        productsTable.grantReadWriteData(getProductListFunction);
        productsTable.grantReadWriteData(createFunction);
        productsTable.grantReadWriteData(getFunction);
        productsTable.grantReadWriteData(updateFunction);
        productsTable.grantReadWriteData(deleteFunction);

        // API Gateway for REST API
        RestApi api = createAPIGW();

        // Get ProductList
        IResource productListResource = api.getRoot().addResource("getproducts");
        Integration getProductListIntegration = new LambdaIntegration(getProductListFunction);
        productListResource.addMethod(HttpMethod.GET.toString(), getProductListIntegration, methodOptions);
        
        
        // Create Product
        IResource addProductResource = api.getRoot().addResource("addProduct");
        Integration createProductIntegration = new LambdaIntegration(createFunction);
        addProductResource.addMethod(HttpMethod.POST.toString(), createProductIntegration, methodOptions);
        
   
        // Get Product by Id
        IResource getProductResource = api.getRoot().addResource("getProductById").addResource("{id}");
        Integration getProductIntegration = new LambdaIntegration(getFunction);
        getProductResource.addMethod(HttpMethod.GET.toString(), getProductIntegration, methodOptions);
    
        // Update Product
        IResource updateProductResource = api.getRoot().addResource("updateProduct");
        Integration updateProductIntegration = new LambdaIntegration(updateFunction);
        updateProductResource.addMethod(HttpMethod.PUT.toString(), updateProductIntegration, methodOptions);
        

        // Delete Product
        IResource deleteProductResource = api.getRoot().addResource("deleteProduct").addResource("{id}");
        Integration deleteProductIntegration = new LambdaIntegration(deleteFunction);
        deleteProductResource.addMethod(HttpMethod.DELETE.toString(), deleteProductIntegration, methodOptions);
        
        addDocumentation(api, productListResource, addProductResource, getProductResource, updateProductResource,
				deleteProductResource);

           
        
    }

	/**
	 * This method adds  for end point URIs exposed through APIGW. 
	 * @param api
	 * @param productListResource
	 * @param addProductResource
	 * @param getProductResource
	 * @param updateProductResource
	 * @param deleteProductResource
	 */
	private void addDocumentation(RestApi api, IResource productListResource, IResource addProductResource,
			IResource getProductResource, IResource updateProductResource, IResource deleteProductResource) {
		// documentation for getProductList method
        new CfnDocumentationPart(this, "doc-getProductList", CfnDocumentationPartProps.builder()
       		  .location(CfnDocumentationPart.LocationProperty.builder()
       		    .type("METHOD")
       		    .method("GET")
       		    .path(productListResource.getPath())
       		    .build())
       		  .properties("{\"responses\": [{\"code\": 200, \"responseBody\": [{\"id\": \"string\", \"name\": \"string\", \"price\": \"double\"}]},"
       		  		+ " {\"code\": 400, \"responseBody\":{\"message\": \"No record found\"}},"
       		  		+ " {\"code\": 500, \"responseBody\":{\"message\": \"Internal server error\"}}]}")
       		  .restApiId(api.getRestApiId())
       		  .build());


            // documentation for getProductById method
            new CfnDocumentationPart(this, "doc-getProductById", CfnDocumentationPartProps.builder()
           		  .location(CfnDocumentationPart.LocationProperty.builder()
           		    .type("METHOD")
           		    .method("GET")
           		    .path(getProductResource.getPath())
           		    .build())
           		  .properties("{\"responses\": [{\"code\": 200, \"responseBody\": {\"id\": \"string\", \"name\": \"string\", \"price\": \"double\"}},"
           		  		+ " {\"code\": 400, \"responseBody\":{\"message\": \"No record found for the id or Invalid id format\"}},"
           		  		+ " {\"code\": 500, \"responseBody\":{\"message\": \"Internal server error\"}}]}")
           		  .restApiId(api.getRestApiId())
           		  .build());
            
            
            //documentation for addProduct method
            new CfnDocumentationPart(this, "doc-addProduct", CfnDocumentationPartProps.builder()
           		  .location(CfnDocumentationPart.LocationProperty.builder()
           		    .type("METHOD")
           		    .method("POST")
           		    .path(addProductResource.getPath())
           		    .build())
           		  .properties("{\"responses\": [{\"code\": 200, \"responseBody\": {\"id\": \"string\", \"name\": \"string\", \"price\": \"double\"}},"
           		  		+ " {\"code\": 400, \"responseBody\":{\"message\": \"Invalid request, name and price cannot be null or empty\"}},"
           		  	    + " {\"code\": 500, \"responseBody\":{\"message\": \"Internal server error\"}}]}")
           		  .restApiId(api.getRestApiId())
           		  .build());
            
            // documentation for deleteProduct method
            new CfnDocumentationPart(this, "doc-deleteProduct", CfnDocumentationPartProps.builder()
           		  .location(CfnDocumentationPart.LocationProperty.builder()
           		    .type("METHOD")
           		    .method("DELETE")
           		    .path(deleteProductResource.getPath())
           		    .build())
           		  .properties("{\"responses\": [{\"code\": 200, \"responseBody\":{\"message\": \"Record deleted for id:<input id>\"}},"
           		  		+ " {\"code\": 400,  \"responseBody\":{\"message\": \"No record found for id or Invalid UUID\"}},"
           		  		+ " {\"code\": 500,  \"responseBody\":{\"message\": \"Internal server error\"}}]}")
           		  .restApiId(api.getRestApiId())
           		  .build());
            
            // documentation for updateProduct method
            new CfnDocumentationPart(this, "doc-updateProduct", CfnDocumentationPartProps.builder()
           		  .location(CfnDocumentationPart.LocationProperty.builder()
           		    .type("METHOD")
           		    .method("PUT")
           		    .path(updateProductResource.getPath())
           		    .build())
           		  .properties("{\"responses\": [{\"code\": 200,\"responseBody\":{\"id\": \"string\", \"name\": \"string\", \"price\": \"double\"}},"
           		  		+ " {\"code\": 400, \"responseBody\":{\"message\": \"No record found for id or Invalid UUID\"}},"
           		    	+ " {\"code\": 500, \"responseBody\":{\"message\": \"Internal server error\"}}]}")
           		  .restApiId(api.getRestApiId())
           		  .build());

           new CfnDocumentationVersion(this, "docVersion1.1", CfnDocumentationVersionProps.builder()
             .documentationVersion("1.1")
             .restApiId(api.getRestApiId())
             .description("Product API documentation")
             .build());

	}

	/**
	 * 
	 * Creates APIGW with Regional settings.
	 * @return RestApi
	 */
	private RestApi createAPIGW() {
		
		RestApiProps apiGwProps = RestApiProps.builder()
                .restApiName(API_NAME)
                .endpointTypes(List.of(EndpointType.REGIONAL)) // Set the endpoint type to regional
                .deployOptions(StageOptions.builder()
                        .stageName(API_STAGE)
                        .documentationVersion("1.1")
                        .build())
                .description("Product API REST service demo CRUD orperations.")
                .build();
		
        RestApi api = new RestApi(this, "productsApi", apiGwProps);
        
		return api;
	}

	/**
	 * Created COGNITO User Pool, with uses Cognito hosted UI for user SignUp 
	 * @return MethodOptions are used to add authorizer to different APIGW URIs.
	 */
	private MethodOptions createCognitoUserPoolAuthorizer() {
		
		UserPool userPool = UserPool.Builder.create(this, "productUserPool")
                .userPoolName("ProductUserPool")
                .selfSignUpEnabled(true)
                .autoVerify(AutoVerifiedAttrs.builder().email(true).build())
                .signInAliases(SignInAliases.builder().email(true).build())
                .build();

        // Configure the Cognito Hosted UI
        		UserPoolClient.Builder.create(this, "productUserPoolClient")
                .userPool(userPool)
                .userPoolClientName("ProductAPIPoolClient")
                .authFlows(AuthFlow.builder().userPassword(true).userSrp(true).adminUserPassword(true).build())
                .oAuth(OAuthSettings.builder()
                        .callbackUrls(List.of(CALLBACK_URL))
                        .build())
                .build();

        CfnUserPoolDomain userPoolDomain = CfnUserPoolDomain.Builder.create(this, "productsDomain")
                .domain(COGNITO_HOSTED_DOMAIN_UNIQUE) // Replace with your Cognito hosted domain
                .userPoolId(userPool.getUserPoolId())
                .build();

        // Output the hosted domain name
        CfnOutput.Builder.create(this, "CognitoDomainNameOutput")
                .value(userPoolDomain.getDomain())
                .description("Cognito Hosted Domain Name")
                .build();

        // Create the Cognito authorizer for API Gateway
        CognitoUserPoolsAuthorizerProps cognitoAuthorizerProps = CognitoUserPoolsAuthorizerProps.builder()
                .cognitoUserPools(List.of(userPool))
                .build();
        CognitoUserPoolsAuthorizer cognitoAuthorizer =
                new CognitoUserPoolsAuthorizer(this, "productCognitoAuthorizer", cognitoAuthorizerProps);

        MethodOptions methodOptions = MethodOptions.builder()
                .authorizer(cognitoAuthorizer)
                .build();
		return methodOptions;
	}

	/**
	 * Creates DYNAMO DB with provisioned capacity and creates table in it.
	 * @return  Table
	 */
	private Table createDynanoDb() {
		TableProps tableProps = TableProps.builder()
                .tableName(PRODUCT_TABLE_NAME)
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Table productsTable = new Table(this, PRODUCT_TABLE_NAME, tableProps);
		return productsTable;
	}
/**
 * Creates properties for Lambda functions
 * adds JAVA 17 runtime
 * adds arm64 – 64-bit ARM architecture, for the AWS Graviton2 processor.
 * @param handler
 * @return
 */
    private FunctionProps getFunctionProps(final String handler) {
        return FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .architecture(Architecture.ARM_64) // used ARM 64 AWS Graviton2 processor
                .handler(handler)
                .memorySize(LAMBDA_MEMORY_MB)
                .timeout(Duration.seconds(LAMBDA_TIMEOUT))
                .code(Code.fromAsset(LOCATION_FUNCTION_JAR))
                .build();
    }
}