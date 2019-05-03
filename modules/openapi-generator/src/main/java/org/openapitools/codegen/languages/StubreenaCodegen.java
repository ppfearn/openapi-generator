/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import static org.openapitools.codegen.utils.StringUtils.camelize;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.features.BeanValidationFeatures;
import org.openapitools.codegen.languages.features.OptionalFeatures;
import org.openapitools.codegen.languages.features.PerformBeanValidationFeatures;
import org.openapitools.codegen.utils.URLPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

public class StubreenaCodegen extends AbstractJavaCodegen
        implements BeanValidationFeatures, PerformBeanValidationFeatures,
        OptionalFeatures {
    private static final Logger LOGGER = LoggerFactory.getLogger(StubreenaCodegen.class);

    public static final String TITLE = "title";
    public static final String SERVER_PORT = "serverPort";
    public static final String CONFIG_PACKAGE = "configPackage";
    public static final String BASE_PACKAGE = "basePackage";
    public static final String INTERFACE_ONLY = "interfaceOnly";
    public static final String DELEGATE_PATTERN = "delegatePattern";
    public static final String SINGLE_CONTENT_TYPES = "singleContentTypes";
    public static final String VIRTUAL_SERVICE = "virtualService";

    public static final String JAVA_8 = "java8";
    public static final String ASYNC = "async";
    public static final String REACTIVE = "reactive";
    public static final String RESPONSE_WRAPPER = "responseWrapper";
    public static final String USE_TAGS = "useTags";
    public static final String SPRING_MVC_LIBRARY = "spring-mvc";
    public static final String SPRING_BOOT = "spring-boot";
    public static final String SPRING_CLOUD_LIBRARY = "spring-cloud";
    public static final String IMPLICIT_HEADERS = "implicitHeaders";
    public static final String OPENAPI_DOCKET_CONFIG = "swaggerDocketConfig";
    public static final String API_FIRST = "apiFirst";
    public static final String HATEOAS = "hateoas";
    public static final String RETURN_SUCCESS_CODE = "returnSuccessCode";

    protected String title = "OpenAPI Stubreena";
    protected String configPackage = "org.openapitools.configuration";
    protected String basePackage = "org.openapitools";
    protected boolean interfaceOnly = false;
    protected boolean delegatePattern = false;
    protected boolean delegateMethod = false;
    protected boolean singleContentTypes = false;
    protected boolean java8 = true;
    protected boolean async = false;
    protected boolean reactive = false;
    protected String responseWrapper = "";
    protected boolean useTags = false;
    protected boolean useBeanValidation = true;
    protected boolean performBeanValidation = false;
    protected boolean implicitHeaders = false;
    protected boolean openapiDocketConfig = false;
    protected boolean apiFirst = false;
    protected boolean useOptional = false;
    protected boolean virtualService = false;
    protected boolean hateoas = false;
    protected boolean returnSuccessCode = false;
    
    // Stubreena specific config 
    
    // A mongo property definition. These are generated on the fly when operations
    // are read in addMongoPropertiesFromOperations and used to auto-generate properties
    // on models in postProcessModels
    static class MongoProperty {
    	
		String name;
    	String accountType;
    	
    	@Override
    	public String toString() {
    		return "name: " + name + " accountType: " + accountType;
    	}
    	
    	@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((accountType == null) ? 0 : accountType.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}
    	
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MongoProperty other = (MongoProperty) obj;
			if (accountType == null) {
				if (other.accountType != null)
					return false;
			} else if (!accountType.equals(other.accountType))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
    }
    
    // Any model in this list will not result in a mongo collection
    // By default any model returned from operations will result in a @Document(collection="xxx") being
    // generated. However some classes have "containers" and these are the models that should
    // be associated to a mongo collection, not the contained model. 
    // Typically this is for multiple responses that re required according to some request parameter,
    // for example bill resources are requested by sequence number and this requires map as a wrapper.
    private List<String> exclusionsFromMongoCollections = new ArrayList<>();
    {
    	exclusionsFromMongoCollections.add("BillSummary");
    }
    
    private Map<String, String> mongoCollections = new HashMap<>();
    /* These static definitions are not required as they are automatically added when the 
     * The operations are read by a call to the addMongoPropertiesFromOperations method
    {
    	mongoCollections.put("BillingAccount", "billing-accounts");
    	mongoCollections.put("AddOns", "addons");
    	mongoCollections.put("AllowancesData", "allowances-data");
    	mongoCollections.put("AllowancesPrepay", "allowances-prepay");
    	mongoCollections.put("AllowancesPostpay", "allowances-postpay");
    	mongoCollections.put("AtpInlifeEligibility", "atp-inlife-eligibility");
    	mongoCollections.put("AddOnsAvailable", "add-ons-available");
    	mongoCollections.put("AvailableBoosts", "boosts-available");
    	mongoCollections.put("Benefits", "benefits");
    	mongoCollections.put("InsideAllowanceItemsBilled", "billed-inside-allowance-items");
    	mongoCollections.put("InsideAllowanceSummariesBilled", "billed-inside-allowance-summaries");
    	mongoCollections.put("OutsideAllowanceItemsBilled", "billed-outside-allowance-items");
    	mongoCollections.put("OutsideAllowanceSummariesBilled", "billed-outside-allowance-summaries");
    	mongoCollections.put("Bills", "bills");
    	mongoCollections.put("BillSummary", "bill-summary");
    	mongoCollections.put("Boosts", "boosts");
    	mongoCollections.put("ContentLock", "content-lock");
    	mongoCollections.put("Contract", "contract");
    	mongoCollections.put("DataGifts", "data-gifts");
    	mongoCollections.put("Device", "device-used");
    	mongoCollections.put("FreeDataUsage", "free-data-usage");
    	mongoCollections.put("InlifeRecommendations", "inlife-recommendations");
    	mongoCollections.put("LoyaltyStamp", "loyalty-stamps");
    	mongoCollections.put("ExtraCharges", "extra-charges");
    	mongoCollections.put("PacksAvailable", "packs-available");
    	mongoCollections.put("Packs", "packs");
    	mongoCollections.put("Payments", "payments");
    	mongoCollections.put("Person", "person-identities");
//    	mongoCollections.put("??", "person-credentials");
    	mongoCollections.put("PrepayCredit", "prepay-credit");
    	mongoCollections.put("PrepayUsageItems", "prepay-usage-items");
    	mongoCollections.put("Rollover", "rollover");
    	mongoCollections.put("OrderBasket", "orders");
//    	mongoCollections.put("??", "credit-cards"); 
    	mongoCollections.put("Rollover", "rollover");
//    	mongoCollections.put("SecurityQuestionAssociation", "security-answers");
    	mongoCollections.put("SpendLimits", "spend-limits");
    	mongoCollections.put("MobileSubscription", "mobile-subscriptions");
    	mongoCollections.put("SubscriptionControls", "subscription-control");
    	mongoCollections.put("Topups", "topups");
    	mongoCollections.put("InsideAllowanceItemsUnbilled", "unbilled-inside-allowance-items");
    	mongoCollections.put("InsideAllowanceSummariesUnbilled", "unbilled-inside-allowance-summaries");
    	mongoCollections.put("OutsideAllowanceItemsUnbilled", "unbilled-outside-allowance-items");
    	mongoCollections.put("OutsideAllowanceSummariesUnbilled", "unbilled-outside-allowance-summaries");
    	mongoCollections.put("UnbilledUsage", "unbilled-usage");
    	mongoCollections.put("UpgradeEligibility", "upgrade-eligibility");
    	
    	// Account Apis either missing or new after first pass
    	mongoCollections.put("AddlineEligibility", "add-line-eligibility");
    	mongoCollections.put("Beneficiaries", "beneficiaries");
    	mongoCollections.put("DataAlert", "data-alert");
    	mongoCollections.put("PairedDevices", "paired-devices");
    	
    	
    }
    */
    
    // List of endpoints or apis to be generated.
    // This allows any resources to be added to the "parent" model as a
    // Dbref in mongo models if the resource matches the operation "tag"
    // So all models with a tag of "mobile-subscription" for example will
    // be added as Dbref (JsonIgnore) properties on MobileSubscription
    private Map<String, String> apiEndpoints = new HashMap<>();
    
    {
    	apiEndpoints.put("BillingAccount", "billing-account");
    	apiEndpoints.put("MobileSubscription", "mobile-subscription");
    	apiEndpoints.put("Person", "person-identities");
    }
    
    // A map of generated class names to stubreena class names
    // And entries will change the name of the generated class reference
    private Map<String, String> modelClassMappings = new HashMap<>();
    
    {
    	modelClassMappings.put("MobileSubscriptionPerson", "MobileSubscription");
    	modelClassMappings.put("MobileSubscriptionBilling", "MobileSubscription");
    	modelClassMappings.put("BillingAccountPerson", "BillingAccount");
    	modelClassMappings.put("BillSummary", "BillSummaryContainer");
    	modelClassMappings.put("LocalDate", "String");
    	modelClassMappings.put("OffsetDateTime", "String");
    	
    }
    
    
    // A list of nested mongo types that are Dbrefs in mongo, but must also
    // be visible in the response and not ignores. For example
    // Subscriptions in accounts and Accounts in Person
    private List<String> nestedMongoTypes = new ArrayList<>();
    
    {
    	nestedMongoTypes.add("MobileSubscriptionBilling");
    	nestedMongoTypes.add("MobileSubscription");
    	nestedMongoTypes.add("BillingAccountPerson");
    	nestedMongoTypes.add("BillingAccount");
    	
    }
    
    // Map of static models that api cannot generate that are specific to subs
    // typically these are a container for bill resources that are stored in 
    // mongo and keyed in a "mp" lookup against bill-sequence-number
    private Map<String, String> staticModelsMap = new HashMap<>();
    {
    	staticModelsMap.put("BillSummaryContainer.java", "billSummaryContainer.mustache");
    	staticModelsMap.put("MongoIdContainer.java", "mongoIdContainer.mustache");
    	staticModelsMap.put("LinkContainer.java", "linkContainer.mustache");
    	staticModelsMap.put("Meta.java", "meta.mustache");
    }
    
    // renaming any "id" property to something more meaningful as mongo contains "id" resource by default
	// changing resource, getter, and setter. But not the JsonResource name as this will be passed
	// back in the response, therefore keeping the API inline without having to change it
    private Map<String, String> propertyNameMap = new HashMap<>();
    {
    	propertyNameMap.put("RecurringTopups.id", "topupId");
    }
    
    private Map<String, List<MongoProperty>> nestedMongoProperties = new HashMap<>();
    
    public StubreenaCodegen() {
        super();
        outputFolder = "generated-code/javaSpring";
        apiTestTemplateFiles.clear(); // TODO: add test template
        embeddedTemplateDir = templateDir = "JavaSpring";
        apiPackage = "org.openapitools.api";
        modelPackage = "org.openapitools.model";
        invokerPackage = "org.openapitools.api";
        artifactId = "openapi-spring";

        // spring uses the jackson lib
        additionalProperties.put("jackson", "true");

        cliOptions.add(new CliOption(TITLE, "server title name or client service name"));
        cliOptions.add(new CliOption(CONFIG_PACKAGE, "configuration package for generated code"));
        cliOptions.add(new CliOption(BASE_PACKAGE, "base package (invokerPackage) for generated code"));
        cliOptions.add(CliOption.newBoolean(INTERFACE_ONLY, "Whether to generate only API interface stubs without the server files.", interfaceOnly));
        cliOptions.add(CliOption.newBoolean(DELEGATE_PATTERN, "Whether to generate the server files using the delegate pattern", delegatePattern));
        cliOptions.add(CliOption.newBoolean(SINGLE_CONTENT_TYPES, "Whether to select only one produces/consumes content-type by operation.", singleContentTypes));
        cliOptions.add(CliOption.newBoolean(JAVA_8, "use java8 default interface", java8));
        cliOptions.add(CliOption.newBoolean(ASYNC, "use async Callable controllers", async));
        cliOptions.add(CliOption.newBoolean(REACTIVE, "wrap responses in Mono/Flux Reactor types (spring-boot only)", reactive));
        cliOptions.add(new CliOption(RESPONSE_WRAPPER, "wrap the responses in given type (Future,Callable,CompletableFuture,ListenableFuture,DeferredResult,HystrixCommand,RxObservable,RxSingle or fully qualified type)"));
        cliOptions.add(CliOption.newBoolean(VIRTUAL_SERVICE, "Generates the virtual service. For more details refer - https://github.com/elan-venture/virtualan/wiki"));
        cliOptions.add(CliOption.newBoolean(USE_TAGS, "use tags for creating interface and controller classnames", useTags));
        cliOptions.add(CliOption.newBoolean(USE_BEANVALIDATION, "Use BeanValidation API annotations", useBeanValidation));
        cliOptions.add(CliOption.newBoolean(PERFORM_BEANVALIDATION, "Use Bean Validation Impl. to perform BeanValidation", performBeanValidation));
        cliOptions.add(CliOption.newBoolean(IMPLICIT_HEADERS, "Skip header parameters in the generated API methods using @ApiImplicitParams annotation.", implicitHeaders));
        cliOptions.add(CliOption.newBoolean(OPENAPI_DOCKET_CONFIG, "Generate Spring OpenAPI Docket configuration class.", openapiDocketConfig));
        cliOptions.add(CliOption.newBoolean(API_FIRST, "Generate the API from the OAI spec at server compile time (API first approach)", apiFirst));
        cliOptions.add(CliOption.newBoolean(USE_OPTIONAL,"Use Optional container for optional parameters", useOptional));
        cliOptions.add(CliOption.newBoolean(HATEOAS, "Use Spring HATEOAS library to allow adding HATEOAS links", hateoas));
        cliOptions.add(CliOption.newBoolean(RETURN_SUCCESS_CODE, "Generated server returns 2xx code", returnSuccessCode));

        supportedLibraries.put(SPRING_BOOT, "Spring-boot Server application using the SpringFox integration.");
        supportedLibraries.put(SPRING_MVC_LIBRARY, "Spring-MVC Server application using the SpringFox integration.");
        supportedLibraries.put(SPRING_CLOUD_LIBRARY, "Spring-Cloud-Feign client with Spring-Boot auto-configured settings.");
        setLibrary(SPRING_BOOT);

        CliOption library = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        library.setDefault(SPRING_BOOT);
        library.setEnum(supportedLibraries);
        cliOptions.add(library);
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
    	return "stubreena";
    }

    @Override
    public String getHelp() {
        return "Generates a Java SpringBoot Server application using the SpringFox integration.";
    }

    @Override
    public void processOpts() {

        List<Pair<String,String>> configOptions = additionalProperties.entrySet().stream()
                .filter(e -> !Arrays.asList(API_FIRST, "hideGenerationTimestamp").contains(e.getKey()))
                .filter(e -> cliOptions.stream().map(CliOption::getOpt).anyMatch(opt -> opt.equals(e.getKey())))
                .map(e -> Pair.of(e.getKey(), e.getValue().toString()))
                .collect(Collectors.toList());
        additionalProperties.put("configOptions", configOptions);

        // Process java8 option before common java ones to change the default dateLibrary to java8.
        LOGGER.info("----------------------------------");
        if (additionalProperties.containsKey(JAVA_8)) {
            LOGGER.info("has JAVA8");
            this.setJava8(Boolean.valueOf(additionalProperties.get(JAVA_8).toString()));
            additionalProperties.put(JAVA_8, java8);
        }
        if (this.java8 && !additionalProperties.containsKey(DATE_LIBRARY)) {
            setDateLibrary("java8");
        }

        if (!additionalProperties.containsKey(BASE_PACKAGE) && additionalProperties.containsKey(CodegenConstants.INVOKER_PACKAGE)) {
            // set invokerPackage as basePackage:
            this.setBasePackage((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE));
            additionalProperties.put(BASE_PACKAGE, basePackage);
            LOGGER.info("Set base package to invoker package (" + basePackage + ")");
        }

        super.processOpts();

        // clear model and api doc template as this codegen
        // does not support auto-generated markdown doc at the moment
        //TODO: add doc templates
        modelDocTemplateFiles.remove("model_doc.mustache");
        apiDocTemplateFiles.remove("api_doc.mustache");

        if (additionalProperties.containsKey(TITLE)) {
            this.setTitle((String) additionalProperties.get(TITLE));
        }

        if (additionalProperties.containsKey(CONFIG_PACKAGE)) {
            this.setConfigPackage((String) additionalProperties.get(CONFIG_PACKAGE));
        } else {
            additionalProperties.put(CONFIG_PACKAGE, configPackage);
        }

        if (additionalProperties.containsKey(BASE_PACKAGE)) {
            this.setBasePackage((String) additionalProperties.get(BASE_PACKAGE));
        } else {
            additionalProperties.put(BASE_PACKAGE, basePackage);
        }
        
        if (additionalProperties.containsKey(VIRTUAL_SERVICE)) {
            this.setVirtualService(Boolean.valueOf(additionalProperties.get(VIRTUAL_SERVICE).toString()));
        }

        if (additionalProperties.containsKey(INTERFACE_ONLY)) {
            this.setInterfaceOnly(Boolean.valueOf(additionalProperties.get(INTERFACE_ONLY).toString()));
        }

        if (additionalProperties.containsKey(DELEGATE_PATTERN)) {
            this.setDelegatePattern(Boolean.valueOf(additionalProperties.get(DELEGATE_PATTERN).toString()));
        }

        if (additionalProperties.containsKey(SINGLE_CONTENT_TYPES)) {
            this.setSingleContentTypes(Boolean.valueOf(additionalProperties.get(SINGLE_CONTENT_TYPES).toString()));
        }

        if (additionalProperties.containsKey(ASYNC)) {
            this.setAsync(Boolean.valueOf(additionalProperties.get(ASYNC).toString()));
            //fix for issue/1164
            convertPropertyToBooleanAndWriteBack(ASYNC);
        }

        if (additionalProperties.containsKey(REACTIVE)) {
            if (!library.equals(SPRING_BOOT)) {
                throw new IllegalArgumentException("Currently, reactive option is only supported with Spring-boot");
            }
            this.setReactive(Boolean.valueOf(additionalProperties.get(REACTIVE).toString()));
        }

        if (additionalProperties.containsKey(RESPONSE_WRAPPER)) {
            this.setResponseWrapper((String) additionalProperties.get(RESPONSE_WRAPPER));
        }

        if (additionalProperties.containsKey(USE_TAGS)) {
            this.setUseTags(Boolean.valueOf(additionalProperties.get(USE_TAGS).toString()));
        }

        if (additionalProperties.containsKey(USE_BEANVALIDATION)) {
            this.setUseBeanValidation(convertPropertyToBoolean(USE_BEANVALIDATION));
        }
        writePropertyBack(USE_BEANVALIDATION, useBeanValidation);

        if (additionalProperties.containsKey(PERFORM_BEANVALIDATION)) {
            this.setPerformBeanValidation(convertPropertyToBoolean(PERFORM_BEANVALIDATION));
        }
        writePropertyBack(PERFORM_BEANVALIDATION, performBeanValidation);

        if (additionalProperties.containsKey(USE_OPTIONAL)) {
            this.setUseOptional(convertPropertyToBoolean(USE_OPTIONAL));
        }

        if (additionalProperties.containsKey(IMPLICIT_HEADERS)) {
            this.setImplicitHeaders(Boolean.valueOf(additionalProperties.get(IMPLICIT_HEADERS).toString()));
        }

        if (additionalProperties.containsKey(OPENAPI_DOCKET_CONFIG)) {
            this.setOpenapiDocketConfig(Boolean.valueOf(additionalProperties.get(OPENAPI_DOCKET_CONFIG).toString()));
        }

        if (additionalProperties.containsKey(API_FIRST)) {
            this.setApiFirst(Boolean.valueOf(additionalProperties.get(API_FIRST).toString()));
        }
        
        if (additionalProperties.containsKey(HATEOAS)) {
            this.setHateoas(Boolean.valueOf(additionalProperties.get(HATEOAS).toString()));
        }

        if (additionalProperties.containsKey(RETURN_SUCCESS_CODE)) {
            this.setReturnSuccessCode(Boolean.valueOf(additionalProperties.get(RETURN_SUCCESS_CODE).toString()));
        }

        typeMapping.put("file", "Resource");
        importMapping.put("Resource", "org.springframework.core.io.Resource");

        if (useOptional) {
            writePropertyBack(USE_OPTIONAL, useOptional);
        }

        if (this.interfaceOnly && this.delegatePattern) {
            if (this.java8) {
                this.delegateMethod = true;
                additionalProperties.put("delegate-method", true);
            } else {
                throw new IllegalArgumentException(
                        String.format(Locale.ROOT, "Can not generate code with `%s` and `%s` true while `%s` is false.",
                                DELEGATE_PATTERN, INTERFACE_ONLY, JAVA_8));
            }
        }

        supportingFiles.add(new SupportingFile("pom.mustache", "", "pom.xml"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));

        if (!this.interfaceOnly) {
            if (library.equals(SPRING_BOOT)) {
//                supportingFiles.add(new SupportingFile("openapi2SpringBoot.mustache",
//                        (sourceFolder + File.separator + basePackage).replace(".", java.io.File.separator), "OpenAPI2SpringBoot.java"));
                supportingFiles.add(new SupportingFile("RFC3339DateFormat.mustache",
                        (sourceFolder + File.separator + basePackage).replace(".", java.io.File.separator), "RFC3339DateFormat.java"));
            }
            if (library.equals(SPRING_MVC_LIBRARY)) {
                supportingFiles.add(new SupportingFile("webApplication.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "WebApplication.java"));
                supportingFiles.add(new SupportingFile("webMvcConfiguration.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "WebMvcConfiguration.java"));
                supportingFiles.add(new SupportingFile("openapiUiConfiguration.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "OpenAPIUiConfiguration.java"));
                supportingFiles.add(new SupportingFile("RFC3339DateFormat.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "RFC3339DateFormat.java"));
            }
            if (library.equals(SPRING_CLOUD_LIBRARY)) {
                supportingFiles.add(new SupportingFile("apiKeyRequestInterceptor.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "ApiKeyRequestInterceptor.java"));
                supportingFiles.add(new SupportingFile("clientConfiguration.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "ClientConfiguration.java"));
                apiTemplateFiles.put("apiClient.mustache", "Client.java");
                if (!additionalProperties.containsKey(SINGLE_CONTENT_TYPES)) {
                    additionalProperties.put(SINGLE_CONTENT_TYPES, "true");
                    this.setSingleContentTypes(true);
                }
            } else {
//                apiTemplateFiles.put("apiController.mustache", "Controller.java");
                supportingFiles.add(new SupportingFile("application.mustache",
                        ("src.main.resources").replace(".", java.io.File.separator), "application.properties"));
                supportingFiles.add(new SupportingFile("homeController.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "HomeController.java"));
                if (!this.reactive && !this.apiFirst) {
                    supportingFiles.add(new SupportingFile("openapiDocumentationConfig.mustache",
                            (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "OpenAPIDocumentationConfig.java"));
                } else {
                    supportingFiles.add(new SupportingFile("openapi.mustache",
                            ("src/main/resources").replace("/", java.io.File.separator), "openapi.yaml"));
                }
            }
        } else if (this.openapiDocketConfig && !library.equals(SPRING_CLOUD_LIBRARY) && !this.reactive && !this.apiFirst) {
            supportingFiles.add(new SupportingFile("openapiDocumentationConfig.mustache",
                    (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "OpenAPIDocumentationConfig.java"));
        }

        if (!SPRING_CLOUD_LIBRARY.equals(library)) {
            supportingFiles.add(new SupportingFile("apiUtil.mustache",
                    (sourceFolder + File.separator + apiPackage).replace(".", java.io.File.separator), "ApiUtil.java"));
        }

        if (this.apiFirst) {
            apiTemplateFiles.clear();
            modelTemplateFiles.clear();
        }

        if ("threetenbp".equals(dateLibrary)) {
            supportingFiles.add(new SupportingFile("customInstantDeserializer.mustache",
                    (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "CustomInstantDeserializer.java"));
            if (library.equals(SPRING_BOOT) || library.equals(SPRING_CLOUD_LIBRARY)) {
                supportingFiles.add(new SupportingFile("jacksonConfiguration.mustache",
                        (sourceFolder + File.separator + configPackage).replace(".", java.io.File.separator), "JacksonConfiguration.java"));
            }
        }

        if ((!this.delegatePattern && this.java8) || this.delegateMethod) {
            additionalProperties.put("jdk8-no-delegate", true);
        }


        if (this.delegatePattern && !this.delegateMethod) {
            additionalProperties.put("isDelegate", "true");
            apiTemplateFiles.put("apiDelegate.mustache", "Delegate.java");
        }

        if (this.java8) {
            additionalProperties.put("javaVersion", "1.8");
            if (!SPRING_CLOUD_LIBRARY.equals(library)) {
                additionalProperties.put("jdk8", "true");
            }
            if (this.async) {
                additionalProperties.put(RESPONSE_WRAPPER, "CompletableFuture");
            }
            if (this.reactive) {
                additionalProperties.put(RESPONSE_WRAPPER, "Mono");
            }
        } else if (this.async) {
            additionalProperties.put(RESPONSE_WRAPPER, "Callable");
        }

        if(!this.apiFirst && !this.reactive) {
            additionalProperties.put("useSpringfox", true);
        }


        // Some well-known Spring or Spring-Cloud response wrappers
        switch (this.responseWrapper) {
            case "Future":
            case "Callable":
            case "CompletableFuture":
                additionalProperties.put(RESPONSE_WRAPPER, "java.util.concurrent." + this.responseWrapper);
                break;
            case "ListenableFuture":
                additionalProperties.put(RESPONSE_WRAPPER, "org.springframework.util.concurrent.ListenableFuture");
                break;
            case "DeferredResult":
                additionalProperties.put(RESPONSE_WRAPPER, "org.springframework.web.context.request.async.DeferredResult");
                break;
            case "HystrixCommand":
                additionalProperties.put(RESPONSE_WRAPPER, "com.netflix.hystrix.HystrixCommand");
                break;
            case "RxObservable":
                additionalProperties.put(RESPONSE_WRAPPER, "rx.Observable");
                break;
            case "RxSingle":
                additionalProperties.put(RESPONSE_WRAPPER, "rx.Single");
                break;
            default:
                break;
        }

        // add lambda for mustache templates
        additionalProperties.put("lambdaEscapeDoubleQuote",
                (Mustache.Lambda) (fragment, writer) -> writer.write(fragment.execute().replaceAll("\"", Matcher.quoteReplacement("\\\""))));
        additionalProperties.put("lambdaRemoveLineBreak",
                (Mustache.Lambda) (fragment, writer) -> writer.write(fragment.execute().replaceAll("\\r|\\n", "")));
        
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        if((library.equals(SPRING_BOOT) || library.equals(SPRING_MVC_LIBRARY)) && !useTags) {
            String basePath = resourcePath;
            if (basePath.startsWith("/")) {
                basePath = basePath.substring(1);
            }
            int pos = basePath.indexOf("/");
            if (pos > 0) {
                basePath = basePath.substring(0, pos);
            }

            if (basePath.equals("")) {
                basePath = "default";
            } else {
                co.subresourceOperation = !co.path.isEmpty();
            }
            List<CodegenOperation> opList = operations.computeIfAbsent(basePath, k -> new ArrayList<>());
            opList.add(co);
            co.baseName = basePath;
        } else {
            super.addOperationToGroup(tag, resourcePath, operation, co, operations);
        }
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        /* TODO the following logic should not need anymore in OAS 3.0
        if ("/".equals(swagger.getBasePath())) {
            swagger.setBasePath("");
        }
        */

        if(!additionalProperties.containsKey(TITLE)) {
            // From the title, compute a reasonable name for the package and the API
            String title = openAPI.getInfo().getTitle();

            // Drop any API suffix
            if (title != null) {
                title = title.trim().replace(" ", "-");
                if (title.toUpperCase(Locale.ROOT).endsWith("API")) {
                    title = title.substring(0, title.length() - 3);
                }

                this.title = camelize(sanitizeName(title), true);
            }
            additionalProperties.put(TITLE, this.title);
        }

        if(!additionalProperties.containsKey(SERVER_PORT)) {
            URL url = URLPathUtils.getServerURL(openAPI);
            this.additionalProperties.put(SERVER_PORT, URLPathUtils.getPort(url, 8080));
        }

        if (openAPI.getPaths() != null) {
            for (String pathname : openAPI.getPaths().keySet()) {
                PathItem path = openAPI.getPaths().get(pathname);
                if (path.readOperations() != null) {
                    for (Operation operation : path.readOperations()) {
                        if (operation.getTags() != null) {
                            List<Map<String, String>> tags = new ArrayList<Map<String, String>>();
                            for (String tag : operation.getTags()) {
                                Map<String, String> value = new HashMap<String, String>();
                                value.put("tag", tag);
                                value.put("hasMore", "true");
                                tags.add(value);
                            }
                            if (tags.size() > 0) {
                                tags.get(tags.size() - 1).remove("hasMore");
                            }
                            if (operation.getTags().size() > 0) {
                                String tag = operation.getTags().get(0);
                                operation.setTags(Arrays.asList(tag));
                            }
                            operation.addExtension("x-tags", tags);
                        }
                    }
                }
            }
        }
    }

    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
    	// first get mongo properties from operations
    	addMongoPropertiesFromOperations(objs);
    	
    	Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            for (final CodegenOperation operation : ops) {
            	List<CodegenResponse> responses = operation.responses;
                if (responses != null) {
                    for (final CodegenResponse resp : responses) {
                        if ("0".equals(resp.code)) {
                            resp.code = "200";
                        }
                        doDataTypeAssignment(resp.dataType, new DataTypeAssigner() {
                            @Override
                            public void setReturnType(final String returnType) {
                                resp.dataType = returnType;
                            }

                            @Override
                            public void setReturnContainer(final String returnContainer) {
                                resp.containerType = returnContainer;
                            }
                        });
                    }
                }

                doDataTypeAssignment(operation.returnType, new DataTypeAssigner() {

                    @Override
                    public void setReturnType(final String returnType) {
                        operation.returnType = returnType;
                    }

                    @Override
                    public void setReturnContainer(final String returnContainer) {
                        operation.returnContainer = returnContainer;
                    }
                });

                if(implicitHeaders){
                    removeHeadersFromAllParams(operation.allParams);
                }
            }
        }

        return objs;
    }

    private interface DataTypeAssigner {
        void setReturnType(String returnType);
        void setReturnContainer(String returnContainer);
    }

    /**
     *
     * @param returnType The return type that needs to be converted
     * @param dataTypeAssigner An object that will assign the data to the respective fields in the model.
     */
    private void doDataTypeAssignment(String returnType, DataTypeAssigner dataTypeAssigner) {
        final String rt = returnType;
        if (rt == null) {
            dataTypeAssigner.setReturnType("Void");
        } else if (rt.startsWith("List")) {
            int end = rt.lastIndexOf(">");
            if (end > 0) {
                dataTypeAssigner.setReturnType(rt.substring("List<".length(), end).trim());
                dataTypeAssigner.setReturnContainer("List");
            }
        } else if (rt.startsWith("Map")) {
            int end = rt.lastIndexOf(">");
            if (end > 0) {
                dataTypeAssigner.setReturnType(rt.substring("Map<".length(), end).split(",")[1].trim());
                dataTypeAssigner.setReturnContainer("Map");
            }
        } else if (rt.startsWith("Set")) {
            int end = rt.lastIndexOf(">");
            if (end > 0) {
                dataTypeAssigner.setReturnType(rt.substring("Set<".length(), end).trim());
                dataTypeAssigner.setReturnContainer("Set");
            }
        }
    }

    /**
     * This method removes header parameters from the list of parameters and also
     * corrects last allParams hasMore state.
     * @param allParams list of all parameters
     */
    private void removeHeadersFromAllParams(List<CodegenParameter> allParams) {
        if(allParams.isEmpty()){
            return;
        }
        final ArrayList<CodegenParameter> copy = new ArrayList<>(allParams);
        allParams.clear();

        for(CodegenParameter p : copy){
            if(!p.isHeaderParam){
                allParams.add(p);
            }
        }
        if (!allParams.isEmpty()) {
            allParams.get(allParams.size()-1).hasMore =false;
        }
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        generateYAMLSpecFile(objs);
        if(library.equals(SPRING_CLOUD_LIBRARY)) {
            List<CodegenSecurity> authMethods = (List<CodegenSecurity>) objs.get("authMethods");
            if (authMethods != null) {
                for (CodegenSecurity authMethod : authMethods) {
                    authMethod.name = camelize(sanitizeName(authMethod.name), true);
                }
            }
        }
        return objs;
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultApi";
        }
        name = sanitizeName(name);
        return camelize(name) + "Api";
    }

    @Override
    public void setParameterExampleValue(CodegenParameter p) {
        String type = p.baseType;
        if (type == null) {
            type = p.dataType;
        }

        if ("File".equals(type)) {
            String example;

            if (p.defaultValue == null) {
                example = p.example;
            } else {
                example = p.defaultValue;
            }

            if (example == null) {
                example = "/path/to/file";
            }
            example = "new org.springframework.core.io.FileSystemResource(new java.io.File(\"" + escapeText(example) + "\"))";
            p.example = example;
        } else {
            super.setParameterExampleValue(p);
        }
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setConfigPackage(String configPackage) {
        this.configPackage = configPackage;
    }

    public String getConfigPackage() {
        return this.configPackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getBasePackage() {
        return this.basePackage;
    }

    public void setInterfaceOnly(boolean interfaceOnly) { this.interfaceOnly = interfaceOnly; }

    public void setDelegatePattern(boolean delegatePattern) { this.delegatePattern = delegatePattern; }

    public void setSingleContentTypes(boolean singleContentTypes) {
        this.singleContentTypes = singleContentTypes;
    }

    public void setJava8(boolean java8) { this.java8 = java8; }

    public void setVirtualService(boolean virtualService) { this.virtualService = virtualService; }

    public void setAsync(boolean async) { this.async = async; }

    public void setReactive(boolean reactive) { this.reactive = reactive; }

    public void setResponseWrapper(String responseWrapper) { this.responseWrapper = responseWrapper; }

    public void setUseTags(boolean useTags) {
        this.useTags = useTags;
    }

    public void setImplicitHeaders(boolean implicitHeaders) {
        this.implicitHeaders = implicitHeaders;
    }

    public void setOpenapiDocketConfig(boolean openapiDocketConfig) {
        this.openapiDocketConfig = openapiDocketConfig;
    }

    public void setApiFirst(boolean apiFirst) {
        this.apiFirst = apiFirst;
    }
    
    public void setHateoas(boolean hateoas) {
        this.hateoas = hateoas;
    }

    public void setReturnSuccessCode(boolean returnSuccessCode) {
        this.returnSuccessCode = returnSuccessCode;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        if ("null".equals(property.example)) {
            property.example = null;
        }
        
        if (nestedMongoTypes.contains(property.complexType)) {
        	property.vendorExtensions.put("x-is-visible-mongo-dbref", true);
        }

        //Add imports for Jackson
        if (!Boolean.TRUE.equals(model.isEnum)) {
            model.imports.add("JsonProperty");

            if (Boolean.TRUE.equals(model.hasEnums)) {
                model.imports.add("JsonValue");
            }
        } else { // enum class
            //Needed imports for Jackson's JsonCreator
            if (additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonCreator");
            }
        }
        
        // Remove enums as they cause more problems than solve
        if (property.isEnum) {
        	property.isEnum = false;
        	property.datatypeWithEnum = property.dataType;
        }
        
    }

    @Override
    public Map<String, Object> postProcessModelsEnum(Map<String, Object> objs) {
        objs = super.postProcessModelsEnum(objs);

        //Add imports for Jackson
        List<Map<String, String>> imports = (List<Map<String, String>>)objs.get("imports");
        List<Object> models = (List<Object>) objs.get("models");
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            CodegenModel cm = (CodegenModel) mo.get("model");
            // for enum model
            if (Boolean.TRUE.equals(cm.isEnum) && cm.allowableValues != null) {
                cm.imports.add(importMapping.get("JsonValue"));
                Map<String, String> item = new HashMap<String, String>();
                item.put("import", importMapping.get("JsonValue"));
                imports.add(item);
            }
        }

        return objs;
        
    }
    
    private void writeStaticTemplates() throws MustacheException, IOException {
    	// write out static templates
        Mustache.Compiler compiler = Mustache.compiler();
        compiler = processCompiler(compiler);
        
        for (Map.Entry<String, String> templateMap : staticModelsMap.entrySet()) {
        	String template = FileUtils.readFileToString(new File(templateDir,  templateMap.getValue()));
            Template tmpl = compiler.compile(template);
            FileUtils.write(new File(modelFileFolder() + File.separator + templateMap.getKey()), tmpl.execute(new HashMap()));
        }
        
    }

    public void setUseBeanValidation(boolean useBeanValidation) {
        this.useBeanValidation = useBeanValidation;
    }

    public void setPerformBeanValidation(boolean performBeanValidation) {
        this.performBeanValidation = performBeanValidation;
    }

    @Override
    public void setUseOptional(boolean useOptional) {
        this.useOptional = useOptional;
    }
    
    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
//    	System.out.println("XXX: postProcessModels");
    	Map<String, Object> returnObjs = super.postProcessModels(objs);
    	List<Object> models = (List<Object>) returnObjs.get("models");
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            CodegenModel cm = (CodegenModel) mo.get("model");
            if (!exclusionsFromMongoCollections.contains(cm.classname) && mongoCollections.containsKey(cm.classname)) {
            	cm.vendorExtensions.put("x-is-mongo-document", true);
            	cm.vendorExtensions.put("x-mongo-collection", mongoCollections.get(cm.classname));	
            }
            
//            System.out.println("Checking model: " + cm.classname + " against: " + apiEndpoints);
            if (apiEndpoints.containsKey(cm.classname)) {
            	String apiEndpoint = apiEndpoints.get(cm.classname);
            	System.out.println("Got a matching endpoint: " + apiEndpoint);
            	List<MongoProperty> mongoProperties = nestedMongoProperties.get(apiEndpoint);
            	if (mongoProperties != null) {
            		System.out.println("Got mongoProperties for " + cm.classname);
            		for (MongoProperty mongoProperty : mongoProperties) {
            			addMongoProperty(mongoProperty, cm);
            		}
            	}
            }
            
          for (CodegenProperty codegenProperty : cm.getVars()) {
  			if ("Link".equals(codegenProperty.complexType)) {
  				cm.vendorExtensions.put("x-is-link-container", true);
  			}
  			
  			// Check complex generic type eg List<MobileSubscriptionPerson> and change
  			// generic to any mappings eg the full MobileSubscription if required
  			if (modelClassMappings.containsKey(codegenProperty.complexType)) {
//  				System.out.println("Remapping: " + codegenProperty.complexType + " to: " + modelClassMappings.get(codegenProperty.complexType));
  				codegenProperty.datatypeWithEnum = codegenProperty.datatypeWithEnum.replace(codegenProperty.complexType, modelClassMappings.get(codegenProperty.complexType));
  				codegenProperty.complexType = modelClassMappings.get(codegenProperty.complexType);
  				if (nestedMongoTypes.contains(codegenProperty.complexType)) {
  					System.out.println("adding x-is-visible-mongo-dbref to property: " + codegenProperty.name + " in model: " + cm.name);
  					codegenProperty.vendorExtensions.put("x-is-visible-mongo-dbref", true);
  		        }
  			}
  			
  			// Check simple properties eg BillSummary and change the referenced type
  			// to any mappings eg the full BillSummaryContainer if required
  			if (modelClassMappings.containsKey(codegenProperty.datatypeWithEnum)) {
//  				System.out.println("Remapping: " + codegenProperty.datatypeWithEnum + " to: " + modelClassMappings.get(codegenProperty.datatypeWithEnum));
  				codegenProperty.datatypeWithEnum = modelClassMappings.get(codegenProperty.datatypeWithEnum);
  				codegenProperty.baseType = modelClassMappings.get(codegenProperty.baseType);
  				if (nestedMongoTypes.contains(codegenProperty.datatypeWithEnum)) {
  					System.out.println("adding x-is-visible-mongo-dbref to property: " + codegenProperty.name + " in model: " + cm.name);
  					codegenProperty.vendorExtensions.put("x-is-visible-mongo-dbref", true);
  		        }
  			}
  			
  			if (propertyNameMap.containsKey(cm.classname+"."+codegenProperty.name)) {
  				// renaming any "id" property to something more meaningful as mongo contains "id" resource by default
  				// changing resource, getter, and setter. But not the JsonResource name as this will be passed
  				// back in the response, therefore keeping the API inline without having to change it
  				codegenProperty.name = propertyNameMap.get(cm.classname+"."+codegenProperty.name);
  				codegenProperty.setter = "set"+camelize(codegenProperty.name);
  				codegenProperty.getter = "get"+camelize(codegenProperty.name);
  			}
  		}
            
        }
        
        try {
        	writeStaticTemplates();
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
    	return returnObjs;
    }
    
    private void addMongoPropertiesFromOperations(Map<String, Object> objs) {
    	Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            for (final CodegenOperation operation : ops) {
//                if (operation.httpMethod.equals("GET") && operation.tags.size() > 0) {
            	if (operation.tags.size() > 0) {
                	String tagName = operation.tags.get(0).getName();
                	List<MongoProperty> existingMongoProperties = nestedMongoProperties.get(tagName);
                	if (existingMongoProperties == null) {
                		existingMongoProperties = new ArrayList<>();
                		nestedMongoProperties.put(tagName, existingMongoProperties);
                	}
                	System.out.println("Adding: " + operation.returnType + ":" + operation.operationIdOriginal + " to collectionsMap");
                	mongoCollections.put(operation.returnType,  operation.operationIdOriginal);
                	
                	
                	MongoProperty mongoProperty = new MongoProperty();
            		mongoProperty.accountType = (String)operation.vendorExtensions.get("x-account-type");
            		mongoProperty.name = operation.returnType;
            		if (!existingMongoProperties.contains(mongoProperty) && !"resource".equalsIgnoreCase(operation.returnType)) {
            			existingMongoProperties.add(mongoProperty);
            		}
                }
            }
        }
    	
    }

	private void addMongoProperty(MongoProperty mongoProperty, CodegenModel cm) {
		if (mongoProperty.name == null || mongoProperty.name.length() == 0 || mongoProperty.name.equals(cm.name)) {
			return;
		}
		CodegenProperty property = new CodegenProperty();
		property.vendorExtensions.put("x-is-hidden-mongo-dbref", true);
		property.baseName = camelize(mongoProperty.name, true);
		property.name = property.baseName;
		property.datatypeWithEnum = mongoProperty.name;
		
		if ("paym".equals(mongoProperty.accountType)) {
			property.vendorExtensions.put("x-is-paym-property", true);
		} else if ("payg".equals(mongoProperty.accountType)) {
			property.vendorExtensions.put("x-is-payg-property", true);
		}
		property.getter = "get" + mongoProperty.name;
		property.setter = "set" + mongoProperty.name;
		cm.vars.add(property);
		
	}
	
}
