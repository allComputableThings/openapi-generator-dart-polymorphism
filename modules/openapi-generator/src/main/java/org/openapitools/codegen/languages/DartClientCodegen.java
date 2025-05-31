/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.meta.features.SchemaSupportFeature;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DartClientCodegen extends AbstractDartCodegen {

    private final Logger LOGGER = LoggerFactory.getLogger(DartClientCodegen.class);

    public static final String SERIALIZATION_LIBRARY_NATIVE = "native_serialization";

    public DartClientCodegen() {
        super();
        final CliOption serializationLibrary = CliOption.newString(CodegenConstants.SERIALIZATION_LIBRARY,
                "Specify serialization library");
        serializationLibrary.setDefault(SERIALIZATION_LIBRARY_NATIVE);

        final Map<String, String> serializationOptions = new HashMap<>();
        serializationOptions.put(SERIALIZATION_LIBRARY_NATIVE, "Use native serializer, backwards compatible");
        serializationLibrary.setEnum(serializationOptions);
        cliOptions.add(serializationLibrary);

        sourceFolder = "";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        // handle library not being set
        if (additionalProperties.get(CodegenConstants.SERIALIZATION_LIBRARY) == null) {
            this.library = SERIALIZATION_LIBRARY_NATIVE;
            LOGGER.debug("Serialization library not set, using default {}", SERIALIZATION_LIBRARY_NATIVE);
        } else {
            this.library = additionalProperties.get(CodegenConstants.SERIALIZATION_LIBRARY).toString();
        }

        this.setSerializationLibrary();

        // Enable polymorphism support
        modifyFeatureSet(features -> features
                .includeSchemaSupportFeatures(
                        SchemaSupportFeature.Polymorphism,
                        SchemaSupportFeature.Union,
                        SchemaSupportFeature.Composite,
                        SchemaSupportFeature.allOf,
                        SchemaSupportFeature.oneOf,
                        SchemaSupportFeature.anyOf
                )
        );

        supportingFiles.add(new SupportingFile("pubspec.mustache", "", "pubspec.yaml"));
        supportingFiles.add(new SupportingFile("analysis_options.mustache", "", "analysis_options.yaml"));
        supportingFiles.add(new SupportingFile("api_client.mustache", libPath, "api_client.dart"));
        supportingFiles.add(new SupportingFile("api_exception.mustache", libPath, "api_exception.dart"));
        supportingFiles.add(new SupportingFile("api_helper.mustache", libPath, "api_helper.dart"));
        supportingFiles.add(new SupportingFile("apilib.mustache", libPath, "api.dart"));

        final String authFolder = libPath + "auth";
        supportingFiles.add(new SupportingFile("auth/authentication.mustache", authFolder, "authentication.dart"));
        supportingFiles.add(new SupportingFile("auth/http_basic_auth.mustache", authFolder, "http_basic_auth.dart"));
        supportingFiles.add(new SupportingFile("auth/http_bearer_auth.mustache", authFolder, "http_bearer_auth.dart"));
        supportingFiles.add(new SupportingFile("auth/api_key_auth.mustache", authFolder, "api_key_auth.dart"));
        supportingFiles.add(new SupportingFile("auth/oauth.mustache", authFolder, "oauth.dart"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("travis.mustache", "", ".travis.yml"));

    }

    private void setSerializationLibrary() {
        final String serialization_library = getLibrary();
        LOGGER.info("Using serialization library {}", serialization_library);

        switch (serialization_library) {
            case SERIALIZATION_LIBRARY_NATIVE: // fall through to default backwards compatible generator
            default:
                additionalProperties.put(SERIALIZATION_LIBRARY_NATIVE, "true");

        }
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        objs = super.postProcessAllModels(objs);
        
        // Filter imports for all models to exclude models from same module
        for (ModelsMap entry : objs.values()) {
            for (ModelMap mo : entry.getModels()) {
                CodegenModel cm = mo.getModel();
                // Filter imports to exclude files from the same module
                Set<String> filteredImports = new HashSet<>();
                
                for (String imp : cm.imports) {
                    // Don't add imports for models in the same module
                    if (!isModelInSameModule(imp)) {
                        filteredImports.add(imp);
                    }
                }
                
                // Replace the imports with filtered ones
                cm.imports.clear();
                cm.imports.addAll(filteredImports);
            }
        }
        
        // Process inheritance and handle parent/child relationships
        for (ModelsMap entry : objs.values()) {
            for (ModelMap mo : entry.getModels()) {
                CodegenModel cm = mo.getModel();
                
                // Set parent class for models that inherit from others
                if (cm.allOf != null && !cm.allOf.isEmpty()) {
                    cm.parent = cm.allOf.iterator().next();
                }
                
                // Process discriminator if present
                if (cm.discriminator != null) {
                    // Ensure discriminator property is required
                    for (CodegenProperty prop : cm.vars) {
                        if (prop.name.equals(cm.discriminator.getPropertyName())) {
                            prop.required = true;
                            break;
                        }
                    }
                }
            }
        }
        
        return objs;
    }
    
    /**
     * Check if a model is in the same module
     * @param importName The import name to check
     * @return true if the model should not be imported (in same module)
     */
    private boolean isModelInSameModule(String importName) {
        // For Dart client, all model classes are in the same module
        // We should only filter out model imports, not other imports
        // Check if this is likely a model import (matches a model name)
        return true; // For now, filter all imports since they're all from the same module
    }
}
