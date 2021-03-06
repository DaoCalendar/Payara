/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2018-2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.microprofile.openapi.impl.processor;

import fish.payara.microprofile.openapi.api.processor.OASProcessor;
import fish.payara.microprofile.openapi.api.visitor.ApiContext;
import fish.payara.microprofile.openapi.api.visitor.ApiVisitor;
import fish.payara.microprofile.openapi.impl.config.OpenApiConfiguration;
import fish.payara.microprofile.openapi.impl.model.ExtensibleImpl;
import fish.payara.microprofile.openapi.impl.model.ExternalDocumentationImpl;
import fish.payara.microprofile.openapi.impl.model.OpenAPIImpl;
import fish.payara.microprofile.openapi.impl.model.OperationImpl;
import fish.payara.microprofile.openapi.impl.model.PathItemImpl;
import fish.payara.microprofile.openapi.impl.model.callbacks.CallbackImpl;
import fish.payara.microprofile.openapi.impl.model.media.ContentImpl;
import fish.payara.microprofile.openapi.impl.model.media.MediaTypeImpl;
import fish.payara.microprofile.openapi.impl.model.media.SchemaImpl;
import fish.payara.microprofile.openapi.impl.model.parameters.ParameterImpl;
import fish.payara.microprofile.openapi.impl.model.parameters.RequestBodyImpl;
import fish.payara.microprofile.openapi.impl.model.responses.APIResponseImpl;
import fish.payara.microprofile.openapi.impl.model.responses.APIResponsesImpl;
import fish.payara.microprofile.openapi.impl.model.security.SecurityRequirementImpl;
import fish.payara.microprofile.openapi.impl.model.security.SecuritySchemeImpl;
import fish.payara.microprofile.openapi.impl.model.servers.ServerImpl;
import fish.payara.microprofile.openapi.impl.model.tags.TagImpl;
import fish.payara.microprofile.openapi.impl.model.util.ModelUtils;
import fish.payara.microprofile.openapi.impl.visitor.OpenApiWalker;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import org.eclipse.microprofile.openapi.models.ExternalDocumentation;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Reference;
import org.eclipse.microprofile.openapi.models.callbacks.Callback;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.parameters.Parameter.In;
import org.eclipse.microprofile.openapi.models.parameters.Parameter.Style;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.media.Schema.SchemaType;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;
import org.eclipse.microprofile.openapi.models.servers.Server;
import org.eclipse.microprofile.openapi.models.tags.Tag;
import org.glassfish.hk2.classmodel.reflect.AnnotatedElement;
import org.glassfish.hk2.classmodel.reflect.AnnotationModel;
import org.glassfish.hk2.classmodel.reflect.ClassModel;
import org.glassfish.hk2.classmodel.reflect.EnumType;
import org.glassfish.hk2.classmodel.reflect.ExtensibleType;
import org.glassfish.hk2.classmodel.reflect.FieldModel;
import org.glassfish.hk2.classmodel.reflect.MethodModel;
import org.glassfish.hk2.classmodel.reflect.ParameterizedInterfaceModel;
import org.glassfish.hk2.classmodel.reflect.Type;
import org.glassfish.hk2.classmodel.reflect.Types;
import org.glassfish.hk2.classmodel.reflect.ParameterizedType;

/**
 * A processor to parse the application for annotations, to add to the OpenAPI
 * model.
 */
public class ApplicationProcessor implements OASProcessor, ApiVisitor {

    private static final Logger LOGGER = Logger.getLogger(ApplicationProcessor.class.getName());

    /**
     * A list of all classes in the given application.
     */
    private final Types allTypes;

    /**
     * A list of allowed classes for scanning
     */
    private final Set<Type> allowedTypes;

    private final ClassLoader appClassLoader;

    private OpenApiWalker apiWalker;

    /**
     * @param allTypes parsed application classes
     * @param allowedTypes filtered application classes for OpenAPI metadata
     * processing
     * @param appClassLoader the class loader for the application.
     */
    public ApplicationProcessor(Types allTypes, Set<Type> allowedTypes, ClassLoader appClassLoader) {
        this.allTypes = allTypes;
        this.allowedTypes = allowedTypes;
        this.appClassLoader = appClassLoader;
    }

    @Override
    public OpenAPI process(OpenAPI api, OpenApiConfiguration config) {
        if (config == null || !config.getScanDisable()) {
            this.apiWalker = new OpenApiWalker(
                    api,
                    allTypes,
                    config == null ? allowedTypes : config.getValidClasses(allowedTypes),
                    appClassLoader
            );
            apiWalker.accept(this);
        }
        return api;
    }

    // JAX-RS method handlers
    @Override
    public void visitGET(AnnotationModel get, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setGET(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitPOST(AnnotationModel post, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setPOST(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitPUT(AnnotationModel put, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setPUT(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitDELETE(AnnotationModel delete, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setDELETE(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitHEAD(AnnotationModel head, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setHEAD(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitOPTIONS(AnnotationModel options, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setOPTIONS(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitPATCH(AnnotationModel patch, MethodModel element, ApiContext context) {
        if (context.getPath() == null) {
            return;
        }

        // Get or create the path item
        PathItem pathItem = context.getApi().getPaths().getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        Operation operation = new OperationImpl();
        pathItem.setPATCH(operation);
        operation.setOperationId(element.getName());

        // Add the default request
        insertDefaultRequestBody(context, operation, element);

        // Add the default response
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitProduces(AnnotationModel produces, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel && context.getWorkingOperation() != null) {
            for (APIResponse response : context.getWorkingOperation()
                    .getResponses().values()) {

                if (response != null) {
                    // Find the wildcard return type
                    if (response.getContent() != null
                            && response.getContent().getMediaType(javax.ws.rs.core.MediaType.WILDCARD) != null) {
                        MediaType wildcardMedia = response.getContent().getMediaType(javax.ws.rs.core.MediaType.WILDCARD);

                        // Merge the wildcard return type with the valid response types
                        //This keeps the specific details of a reponse type that has a schema
                        List<String> mediaTypes = produces.getValue("value", List.class);
                        for (String mediaType : mediaTypes) {
                            MediaType held = response.getContent().getMediaType(getContentType(mediaType));
                            if (held == null) {
                                response.getContent().addMediaType(getContentType(mediaType), wildcardMedia);
                            } else {
                                MediaTypeImpl.merge(held, wildcardMedia, true);
                            }
                        }
                        // If there is an @Produces, remove the wildcard
                        response.getContent().removeMediaType(javax.ws.rs.core.MediaType.WILDCARD);
                    }
                }
            }
        }
    }

    @Override
    public void visitConsumes(AnnotationModel consumes, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel && context.getWorkingOperation() != null) {
            RequestBody requestBody = context.getWorkingOperation()
                    .getRequestBody();

            if (requestBody != null) {
                // Find the wildcard return type
                if (requestBody.getContent() != null
                        && requestBody.getContent().getMediaType(javax.ws.rs.core.MediaType.WILDCARD) != null) {
                    MediaType wildcardMedia = requestBody.getContent().getMediaType(javax.ws.rs.core.MediaType.WILDCARD);

                    // Copy the wildcard return type to the valid request body types
                    List<String> mediaTypes = consumes.getValue("value", List.class);
                    for (String mediaType : mediaTypes) {
                        requestBody.getContent().addMediaType(getContentType(mediaType), wildcardMedia);
                    }
                    // If there is an @Consumes, remove the wildcard
                    requestBody.getContent().removeMediaType(javax.ws.rs.core.MediaType.WILDCARD);
                }
            }
        }
    }

    @Override
    public void visitQueryParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        addParameter(element, context, param.getValue("value", String.class), In.QUERY, null);
    }

    @Override
    public void visitPathParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        addParameter(element, context, param.getValue("value", String.class), In.PATH, true);
    }

    @Override
    public void visitFormParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        // Find the aggregate schema type of all the parameters
        SchemaType formSchemaType = null;

        if (element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
            List<org.glassfish.hk2.classmodel.reflect.Parameter> parameters = ((org.glassfish.hk2.classmodel.reflect.Parameter) element)
                    .getMethod().getParameters();
            for (org.glassfish.hk2.classmodel.reflect.Parameter methodParam : parameters) {
                if (methodParam.getAnnotation(FormParam.class.getName()) != null) {
                    formSchemaType = ModelUtils.getParentSchemaType(
                            formSchemaType,
                            ModelUtils.getSchemaType(methodParam, context)
                    );
                }
            }
        }

        if (context.getWorkingOperation() != null) {
            // If there's no request body, fill out a new one right down to the schema
            if (context.getWorkingOperation().getRequestBody() == null) {
                context.getWorkingOperation().setRequestBody(new RequestBodyImpl().content(new ContentImpl()
                        .addMediaType(javax.ws.rs.core.MediaType.WILDCARD, new MediaTypeImpl()
                                .schema(new SchemaImpl()))));
            }

            // Set the request body type accordingly.
            context.getWorkingOperation().getRequestBody().getContent()
                    .getMediaType(javax.ws.rs.core.MediaType.WILDCARD).getSchema()
                    .setType(formSchemaType);
        }
    }

    @Override
    public void visitHeaderParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        addParameter(element, context, param.getValue("value", String.class), In.HEADER, null);
    }

    @Override
    public void visitCookieParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        addParameter(element, context, param.getValue("value", String.class), In.COOKIE, null);
    }

    private static void addParameter(AnnotatedElement element, ApiContext context, String name, In in, Boolean required) {
        Parameter newParameter = new ParameterImpl();
        newParameter.setName(name);
        newParameter.setIn(in);
        newParameter.setStyle(Style.SIMPLE);
        newParameter.setRequired(required);
        SchemaImpl schema = new SchemaImpl();
        String defaultValue = getDefaultValueIfPresent(element);

        if (element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
            org.glassfish.hk2.classmodel.reflect.Parameter parameter = (org.glassfish.hk2.classmodel.reflect.Parameter) element;
            schema.setType(ModelUtils.getSchemaType(parameter.getTypeName(), context));
        } else {
            FieldModel field = (FieldModel) element;
            schema.setType(ModelUtils.getSchemaType(field.getTypeName(), context));
        }

        if (schema.getType() == SchemaType.ARRAY) {
            schema.setItems(getArraySchema(element, context));
            if (defaultValue != null) {
                schema.getItems().setDefaultValue(defaultValue);
            }
        } else if (defaultValue != null) {
            schema.setDefaultValue(defaultValue);
        }

        newParameter.setSchema(schema);

        if (context.getWorkingOperation() != null) {
            context.getWorkingOperation().addParameter(newParameter);
        } else {
            LOGGER.log(
                    SEVERE,
                    "Couldn''t add {0} parameter, \"{1}\" to the OpenAPI Document. This is usually caused by declaring parameter under a method with an unsupported annotation.",
                    new Object[]{newParameter.getIn(), newParameter.getName()}
            );
        }
    }

    private static SchemaImpl getArraySchema(AnnotatedElement element, ApiContext context) {
        SchemaImpl arraySchema = new SchemaImpl();
        List<ParameterizedType> parameterizedType;

        if (element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
            org.glassfish.hk2.classmodel.reflect.Parameter parameter = (org.glassfish.hk2.classmodel.reflect.Parameter) element;
            parameterizedType = parameter.getParameterizedTypes();
        } else {
            FieldModel field = (FieldModel) element;
            parameterizedType = field.getParameterizedTypes();
        }

        arraySchema.setType(ModelUtils.getSchemaType(parameterizedType.get(0).getTypeName(), context));
        return arraySchema;
    }

    private static String getDefaultValueIfPresent(AnnotatedElement element) {
        Collection<AnnotationModel> annotations = element.getAnnotations();
        for (AnnotationModel annotation : annotations) {
            if (DefaultValue.class.getName().equals(annotation.getType().getName())) {
                try {
                    return annotation.getValue("value", String.class);
                } catch (Exception ex) {
                    LOGGER.log(WARNING, "Couldn't get the default value", ex);
                }
            }
        }
        return null;
    }

    @Override
    public void visitOpenAPI(AnnotationModel definition, AnnotatedElement element, ApiContext context) {
        OpenAPIImpl.merge(OpenAPIImpl.createInstance(definition, context), context.getApi(), true, context);
    }

    @Override
    public void visitSchema(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        if (element instanceof ClassModel) {
            visitSchemaClass(null, annotation, (ClassModel) element, Collections.emptyList(), context);
        } else if (element instanceof EnumType) {
            vistEnumClass(annotation, (EnumType) element, context);
        } else if (element instanceof FieldModel) {
            visitSchemaField(annotation, (FieldModel) element, context);
        } else if (element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
            visitSchemaParameter(annotation, (org.glassfish.hk2.classmodel.reflect.Parameter) element, context);
        }
    }

    private void vistEnumClass(AnnotationModel schemaAnnotation, EnumType enumType, ApiContext context) {
        // Get the schema object name
        String schemaName = (schemaAnnotation == null) ? null : schemaAnnotation.getValue("name", String.class);
        if (schemaName == null || schemaName.isEmpty()) {
            schemaName = enumType.getSimpleName();
        }
        Schema schema = SchemaImpl.createInstance(schemaAnnotation, context);

        Schema newSchema = new SchemaImpl();
        context.getApi().getComponents().addSchema(schemaName, newSchema);
        if (schema != null) {
            SchemaImpl.merge(schema, newSchema, true, context);
        }
        if (schema == null || schema.getEnumeration().isEmpty()) {
            //if the schema annotation does not specify enums, then all enum fields will be added
            for (FieldModel enumField : enumType.getStaticFields()) {
                newSchema.addEnumeration(enumField);
            }
        }

    }

    private Schema visitSchemaClass(
            Schema schema,
            AnnotationModel schemaAnnotation, ClassModel clazz,
            Collection<ParameterizedInterfaceModel> parameterizedInterfaces,
            ApiContext context) {

        // Get the schema object name
        String schemaName = (schemaAnnotation == null) ? null : schemaAnnotation.getValue("name", String.class);
        if (schemaName == null || schemaName.isEmpty()) {
            schemaName = clazz.getSimpleName();
        }

        // Add a new schema
        if (schema == null) {
            schema = new SchemaImpl();
            context.getApi().getComponents().addSchema(schemaName, schema);
        }

        // If there is an annotation
        if (schemaAnnotation != null) {
            SchemaImpl.merge(SchemaImpl.createInstance(schemaAnnotation, context), schema, true, context);
        }
        for (FieldModel field : clazz.getFields()) {
            if (!field.isTransient() && !field.getName().startsWith("this$")) {
                schema.addProperty(field.getName(), createSchema(null, context, field, clazz, parameterizedInterfaces));
            }
        }

        if (schema.getType() == null) {
            schema.setType(ModelUtils.getSchemaType(clazz.getName(), context));
        }

        // If there is an extending class, add the data
        if (clazz.getParent() != null) {
            ClassModel superClass = clazz.getParent();

            // If the super class is legitimate
            if (superClass != null) {

                // Get the parent schema annotation
                AnnotationModel parentSchemAnnotation = context.getAnnotationInfo(superClass)
                        .getAnnotation(org.eclipse.microprofile.openapi.annotations.media.Schema.class);

                ParameterizedInterfaceModel parameterizedInterface = clazz.getParameterizedInterface(superClass);
                if (parameterizedInterface == null) {
                    // Create a schema for the parent
                    visitSchemaClass(null, parentSchemAnnotation, superClass, Collections.emptyList(), context);

                    // Get the superclass schema name
                    String parentSchemaName = parentSchemAnnotation == null ? null : parentSchemAnnotation.getValue("name", String.class);
                    if (parentSchemaName == null || parentSchemaName.isEmpty()) {
                        parentSchemaName = superClass.getSimpleName();
                    }

                    // Link the schemas
                    schema.addAllOf(new SchemaImpl().ref(parentSchemaName));
                } else {
                    visitSchemaClass(schema, parentSchemAnnotation, superClass, parameterizedInterface.getParametizedTypes(), context);
                }
            }
        }
        return schema;
    }

    public void visitSchemaField(AnnotationModel schemaAnnotation, FieldModel field, ApiContext context) {
        // Get the schema object name
        String schemaName = (schemaAnnotation == null) ? null : schemaAnnotation.getValue("name", String.class);
        if (schemaName == null || schemaName.isEmpty()) {
            schemaName = field.getName();
        }
        Schema schema = SchemaImpl.createInstance(schemaAnnotation, context);

        // Get the parent schema object name
        String parentName = null;
        AnnotationModel classSchemaAnnotation = context.getAnnotationInfo(field.getDeclaringType())
                .getAnnotation(org.eclipse.microprofile.openapi.annotations.media.Schema.class);
        if (classSchemaAnnotation != null) {
            parentName = classSchemaAnnotation.getValue("name", String.class);
        }
        if (parentName == null || parentName.isEmpty()) {
            parentName = field.getDeclaringType().getSimpleName();
        }

        // Get or create the parent schema object
        Map<String, Schema> schemas
                = context.getApi().getComponents().getSchemas();
        Schema parentSchema
                = schemas.getOrDefault(parentName, new SchemaImpl());
        schemas.put(parentName, parentSchema);

        Schema property = new SchemaImpl();
        parentSchema.addProperty(schemaName, property);
        property.setType(ModelUtils.getSchemaType(field.getTypeName(), context));
        SchemaImpl.merge(schema, property, true, context);
    }

    private static void visitSchemaParameter(AnnotationModel schemaAnnotation, org.glassfish.hk2.classmodel.reflect.Parameter parameter, ApiContext context) {
        // If this is being parsed at the start, ignore it as the path doesn't exist
        if (context.getWorkingOperation() == null) {
            return;
        }
        // Check if it's a request body
        if (ModelUtils.isRequestBody(context, parameter)) {
            if (context.getWorkingOperation().getRequestBody() == null) {
                context.getWorkingOperation().setRequestBody(new RequestBodyImpl());
            }
            // Insert the schema to the request body media type
            MediaType mediaType = context.getWorkingOperation().getRequestBody().getContent()
                    .getMediaType(javax.ws.rs.core.MediaType.WILDCARD);
            Schema schema = SchemaImpl.createInstance(schemaAnnotation, context);
            SchemaImpl.merge(schema, mediaType.getSchema(), true, context);
            if (schema.getRef() != null && !schema.getRef().isEmpty()) {
                mediaType.setSchema(new SchemaImpl().ref(schema.getRef()));
            }
        } else if (ModelUtils.getParameterType(context, parameter) != null) {
            for (Parameter param : context.getWorkingOperation()
                    .getParameters()) {
                if (param.getName().equals(ModelUtils.getParameterName(context, parameter))) {
                    Schema schema = SchemaImpl.createInstance(schemaAnnotation, context);
                    SchemaImpl.merge(schema, param.getSchema(), true, context);
                    if (schema.getRef() != null && !schema.getRef().isEmpty()) {
                        param.setSchema(new SchemaImpl().ref(schema.getRef()));
                    }
                }
            }
        }
    }

    @Override
    public void visitExtension(AnnotationModel extension, AnnotatedElement element, ApiContext context) {
        String value = extension.getValue("value", String.class);
        String name = extension.getValue("name", String.class);
        Boolean parseValue = extension.getValue("parseValue", Boolean.class);
        if (name != null && !name.isEmpty()
                && value != null && !value.isEmpty()) {
            Object parsedValue = ExtensibleImpl.convertExtensionValue(value, parseValue);
            if (element instanceof MethodModel) {
                context.getWorkingOperation().addExtension(name, parsedValue);
            } else {
                context.getApi().addExtension(name, parsedValue);
            }
        }
    }

    @Override
    public void visitExtensions(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> extensions = annotation.getValue("value", List.class);
        if (extensions != null) {
            extensions.forEach(extension -> visitExtension(extension, element, context));
        }
    }

    @Override
    public void visitOperation(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        OperationImpl.merge(OperationImpl.createInstance(annotation, context), context.getWorkingOperation(), true);
        // If the operation should be hidden, remove it
        if (annotation.getValue("hidden", Boolean.class)) {
            ModelUtils.removeOperation(context.getApi().getPaths().getPathItem(context.getPath()),
                    context.getWorkingOperation());
        }
    }

    @Override
    public void visitCallback(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel) {
            String name = annotation.getValue("name", String.class);
            Callback callbackModel = context.getWorkingOperation()
                    .getCallbacks().getOrDefault(name, new CallbackImpl());
            context.getWorkingOperation().getCallbacks().put(name, callbackModel);
            CallbackImpl.merge(CallbackImpl.createInstance(annotation, context), callbackModel, true, context);
        }
    }

    @Override
    public void visitCallbacks(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> callbacks = annotation.getValue("value", List.class);
        if (callbacks != null) {
            callbacks.forEach(callback -> visitCallback(callback, element, context));
        }
    }

    @Override
    public void visitRequestBody(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel || element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
            RequestBody currentRequestBody = context
                    .getWorkingOperation().getRequestBody();
            if (currentRequestBody != null || element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
                RequestBodyImpl.merge(RequestBodyImpl.createInstance(annotation, context), currentRequestBody, true, context);
            }
        }
    }

    @Override
    public void visitAPIResponse(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        APIResponseImpl apiResponse = APIResponseImpl.createInstance(annotation, context);
        APIResponsesImpl.merge(apiResponse, context.getWorkingOperation().getResponses(), true, context);

        // If an APIResponse has been processed that isn't the default
        String responseCode = apiResponse.getResponseCode();
        if (responseCode != null && !responseCode.isEmpty() && !responseCode
                .equals(APIResponses.DEFAULT)) {
            // If the element doesn't also contain a response mapping to the default
            AnnotationModel apiResponsesParent = element
                    .getAnnotation(org.eclipse.microprofile.openapi.annotations.responses.APIResponses.class.getName());
            if (apiResponsesParent != null) {
                List<AnnotationModel> apiResponses = apiResponsesParent.getValue("value", List.class);
                if (apiResponses.stream()
                        .map(a -> a.getValue("responseCode", String.class))
                        .noneMatch(code -> code == null || code.isEmpty() || code.equals(APIResponses.DEFAULT))) {
                    // Then remove the default response
                    context.getWorkingOperation().getResponses()
                            .removeAPIResponse(APIResponses.DEFAULT);
                }
            } else {
                context.getWorkingOperation().getResponses()
                        .removeAPIResponse(APIResponses.DEFAULT);
            }
        }
    }

    @Override
    public void visitAPIResponses(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> responses = annotation.getValue("value", List.class);
        if (responses != null) {
            responses.forEach(response -> visitAPIResponse(response, element, context));
        }
    }

    @Override
    public void visitParameters(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> parameters = annotation.getValue("value", List.class);
        if (parameters != null) {
            parameters.forEach(parameter -> visitParameter(parameter, element, context));
        }
    }

    @Override
    public void visitParameter(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        Parameter matchedParam = null;
        Parameter parameter = ParameterImpl.createInstance(annotation, context);

        if (element instanceof org.glassfish.hk2.classmodel.reflect.Parameter) {
            matchedParam = findOperationParameterFor((org.glassfish.hk2.classmodel.reflect.Parameter) element, context);
        }
        if (element instanceof MethodModel) {
            matchedParam = findOperationParameterFor(parameter, (MethodModel) element, context);
        }
        if (matchedParam != null) {
            ParameterImpl.merge(parameter, matchedParam, true, context);

            // If a content was added, and a schema type exists, reconfigure the schema type
            if (matchedParam.getContent() != null
                    && !matchedParam.getContent().values().isEmpty()
                    && matchedParam.getSchema() != null
                    && matchedParam.getSchema().getType() != null) {
                SchemaType type = matchedParam.getSchema().getType();
                matchedParam.setSchema(null);

                for (MediaType mediaType : matchedParam.getContent().values()) {
                    if (mediaType.getSchema() == null) {
                        mediaType.setSchema(new SchemaImpl());
                    }
                    mediaType.getSchema()
                            .setType(ModelUtils.mergeProperty(mediaType.getSchema().getType(), type, false));
                }
            }
        }
    }

    private static Parameter findOperationParameterFor(
            Parameter parameter,
            MethodModel annotated,
            ApiContext context) {
        String name = parameter.getName();
        // If the parameter reference is valid
        if (name != null && !name.isEmpty()) {
            // Get all parameters with the same name
            List<org.glassfish.hk2.classmodel.reflect.Parameter> matchingMethodParameters = annotated.getParameters()
                    .stream()
                    .filter(x -> name.equals(ModelUtils.getParameterName(context, x)))
                    .collect(Collectors.toList());
            // If there is more than one match, filter it further
            In in = parameter.getIn();
            if (matchingMethodParameters.size() > 1 && in != null) {
                // Remove all parameters of the wrong input type
                matchingMethodParameters
                        .removeIf(x -> ModelUtils.getParameterType(context, x) != In.valueOf(in.name()));
            }
            if (matchingMethodParameters.isEmpty()) {
                return null;
            }
            // If there's only one matching parameter, handle it immediately
            String matchingMethodParamName = ModelUtils.getParameterName(context, matchingMethodParameters.get(0));
            // Find the matching operation parameter
            for (Parameter operationParam : context
                    .getWorkingOperation().getParameters()) {
                if (operationParam.getName().equals(matchingMethodParamName)) {
                    return operationParam;
                }
            }
        }
        return null;
    }

    /**
     * Find the matching parameter, and match it
     */
    private static Parameter findOperationParameterFor(
            org.glassfish.hk2.classmodel.reflect.Parameter annotated, ApiContext context) {
        String actualName = ModelUtils.getParameterName(context, annotated);
        if (actualName == null) {
            return null;
        }
        for (Parameter param : context.getWorkingOperation()
                .getParameters()) {
            if (actualName.equals(param.getName())) {
                return param;
            }
        }
        return null;
    }

    @Override
    public void visitExternalDocumentation(AnnotationModel externalDocs, AnnotatedElement element,
            ApiContext context) {
        if (element instanceof MethodModel) {
            ExternalDocumentation newExternalDocs = new ExternalDocumentationImpl();
            ExternalDocumentationImpl.merge(ExternalDocumentationImpl.createInstance(externalDocs), newExternalDocs, true);
            if (newExternalDocs.getUrl() != null && !newExternalDocs.getUrl().isEmpty()) {
                context.getWorkingOperation().setExternalDocs(newExternalDocs);
            }
        }
    }

    @Override
    public void visitServer(AnnotationModel server, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel) {
            Server newServer = new ServerImpl();
            context.getWorkingOperation().addServer(newServer);
            ServerImpl.merge(ServerImpl.createInstance(server, context), newServer, true);
        }
    }

    @Override
    public void visitServers(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> servers = annotation.getValue("value", List.class);
        if (servers != null) {
            servers.forEach(server -> visitServer(server, element, context));
        }
    }

    @Override
    public void visitTag(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        Tag from = TagImpl.createInstance(annotation, context);
        if (element instanceof MethodModel) {
            TagImpl.merge(from, context.getWorkingOperation(), true, context.getApi().getTags());
        } else {
            Tag newTag = new TagImpl();
            TagImpl.merge(from, newTag, true);
            if (newTag.getName() != null && !newTag.getName().isEmpty()) {
                context.getApi().getTags().add(newTag);
            }
        }
    }

    @Override
    public void visitTags(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel) {
            List<AnnotationModel> tags = annotation.getValue("value", List.class);
            if (tags != null) {
                for (AnnotationModel tag : tags) {
                    visitTag(tag, element, context);
                }
            }
            List<String> refs = annotation.getValue("refs", List.class);
            if (refs != null) {
                for (String ref : refs) {
                    if (ref != null && !ref.isEmpty()) {
                        context.getWorkingOperation().addTag(ref);
                    }
                }
            }
        }
    }

    @Override
    public void visitSecurityScheme(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        String securitySchemeName = annotation.getValue("securitySchemeName", String.class);
        SecurityScheme securityScheme = SecuritySchemeImpl.createInstance(annotation, context);
        if (securitySchemeName != null && !securitySchemeName.isEmpty()) {
            SecurityScheme newScheme = context.getApi().getComponents()
                    .getSecuritySchemes().getOrDefault(securitySchemeName, new SecuritySchemeImpl());
            context.getApi().getComponents().addSecurityScheme(securitySchemeName, newScheme);
            SecuritySchemeImpl.merge(securityScheme, newScheme, true);
        }
    }

    @Override
    public void visitSecuritySchemes(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> securitySchemes = annotation.getValue("value", List.class);
        if (securitySchemes != null) {
            securitySchemes.forEach(securityScheme -> visitSecurityScheme(securityScheme, element, context));
        }
    }

    @Override
    public void visitSecurityRequirement(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        if (element instanceof MethodModel) {
            String securityRequirementName = annotation.getValue("name", String.class);
            SecurityRequirement securityRequirement = SecurityRequirementImpl.createInstance(annotation, context);
            if (securityRequirementName != null && !securityRequirementName.isEmpty()) {
                SecurityRequirement model = new SecurityRequirementImpl();
                SecurityRequirementImpl.merge(securityRequirement, model);
                context.getWorkingOperation().addSecurityRequirement(model);
            }
        }
    }

    @Override
    public void visitSecurityRequirements(AnnotationModel annotation, AnnotatedElement element, ApiContext context) {
        List<AnnotationModel> securityRequirements = annotation.getValue("value", List.class);
        if (securityRequirements != null) {
            securityRequirements.forEach(securityRequirement -> visitSecurityRequirement(securityRequirement, element, context));
        }
    }

    // PRIVATE METHODS
    private RequestBody insertDefaultRequestBody(ApiContext context,
            Operation operation, MethodModel method) {
        RequestBody requestBody = new RequestBodyImpl();

        // Get the request body type of the method
        org.glassfish.hk2.classmodel.reflect.ParameterizedType bodyType = null;
        for (org.glassfish.hk2.classmodel.reflect.Parameter methodParam : method.getParameters()) {
            if (ModelUtils.isRequestBody(context, methodParam)) {
                bodyType = methodParam;
                break;
            }
        }
        if (bodyType == null) {
            return null;
        }

        // Create the default request body with a wildcard mediatype
        MediaType mediaType = new MediaTypeImpl().schema(createSchema(context, bodyType));
        requestBody.getContent().addMediaType(javax.ws.rs.core.MediaType.WILDCARD, mediaType);

        operation.setRequestBody(requestBody);
        return requestBody;
    }

    /**
     * Creates a new {@link APIResponse} to model the default response of a
     * {@link Method}, and inserts it into the {@link Operation} responses.
     *
     * @param context the API context.
     * @param operation the {@link Operation} to add the default response to.
     * @param method the {@link Method} to model the default response on.
     * @return the newly created {@link APIResponse}.
     */
    private APIResponse insertDefaultResponse(ApiContext context,
            Operation operation, MethodModel method) {
        APIResponse defaultResponse = new APIResponseImpl();
        defaultResponse.setDescription("Default Response.");

        // Create the default response with a wildcard mediatype
        MediaType mediaType = new MediaTypeImpl().schema(
                createSchema(context, method.getReturnType())
        );
        defaultResponse.getContent().addMediaType(javax.ws.rs.core.MediaType.WILDCARD, mediaType);

        // Add the default response
        operation.setResponses(new APIResponsesImpl().addAPIResponse(
                APIResponses.DEFAULT, defaultResponse));
        return defaultResponse;
    }

    /**
     * @return the {@link javax.ws.rs.core.MediaType} with the given name.
     * Defaults to <code>WILDCARD</code>.
     */
    private static String getContentType(String name) {
        String contentType = javax.ws.rs.core.MediaType.WILDCARD;
        try {
            javax.ws.rs.core.MediaType mediaType = javax.ws.rs.core.MediaType.valueOf(name);
            if (mediaType != null) {
                contentType = mediaType.toString();
            }
        } catch (IllegalArgumentException ex) {
            LOGGER.log(FINE, "Unrecognised content type.", ex);
        }
        return contentType;
    }

    private Schema createSchema(
            ApiContext context,
            ParameterizedType type) {
        return createSchema(null, context, type);
    }

    private Schema createSchema(
            Schema schema,
            ApiContext context,
            ParameterizedType type) {

        String typeName = type.getTypeName();
        List<ParameterizedType> genericTypes = type.getParameterizedTypes();
        SchemaType schemaType = ModelUtils.getSchemaType(type, context);

        if (schema == null) {
            schema = new SchemaImpl();
            schema.setType(schemaType);
        }

        // Set the subtype if it's an array (for example an array of ints)
        if (schemaType == SchemaType.ARRAY) {
            if (type.isArray()) {
                schemaType = ModelUtils.getSchemaType(type.getTypeName(), context);
                schema.setType(schemaType);
            } else if (!genericTypes.isEmpty()) { // should be something Iterable
                schema.setItems(createSchema(context, genericTypes.get(0)));
            }
        }

        // If the schema is an object, insert the reference
        if (schemaType == SchemaType.OBJECT) {
            if (insertObjectReference(context, schema, type.getType(), typeName)) {
                schema.setType(null);
                schema.setItems(null);
            }
        }

        return schema;
    }

    private Schema createSchema(
            Schema schema,
            ApiContext context,
            ParameterizedType type,
            ExtensibleType clazz,
            Collection<ParameterizedInterfaceModel> classParameterizedTypes) {

        if (schema == null) {
            schema = new SchemaImpl();
        }
        SchemaType schemaType = ModelUtils.getSchemaType(type, context);

        // If the annotated element is the same type as the reference class, return a null schema
        if (schemaType == SchemaType.OBJECT && type.getType() != null && type.getType().equals(clazz)) {
            schema.setType(null);
            schema.setItems(null);
            return schema;
        }

        if (type.getType() == null) {
            ParameterizedInterfaceModel classParameterizedType = findParameterizedModelFromGenerics(
                    clazz,
                    classParameterizedTypes,
                    type
            );
            String typeName = null;
            if (type.getTypeName() != null) {
                typeName = type.getTypeName();
            }
            if ((typeName == null || Object.class.getName().equals(typeName)) && classParameterizedType != null) {
                typeName = classParameterizedType.getRawInterfaceName();
            }

            schemaType = ModelUtils.getSchemaType(typeName, context);
            if (schema.getType() == null) {
                schema.setType(schemaType);
            }

            Schema containerSchema = schema;
            if (schemaType == SchemaType.ARRAY) {
                containerSchema = new SchemaImpl();
                schema.setItems(containerSchema);
            }
            if (classParameterizedType != null) {
                Collection<ParameterizedInterfaceModel> genericTypes = classParameterizedType.getParametizedTypes();
                if (genericTypes.isEmpty()) {
                    if (insertObjectReference(context, containerSchema, classParameterizedType.getRawInterface(), classParameterizedType.getRawInterfaceName())) {
                        containerSchema.setType(null);
                        containerSchema.setItems(null);
                    }
                } else if (classParameterizedType.getRawInterface() instanceof ClassModel) {
                    visitSchemaClass(containerSchema, null, (ClassModel) classParameterizedType.getRawInterface(), genericTypes, context);
                } else {
                    LOGGER.log(FINE, "Unrecognised schema {0} class found.", new Object[]{classParameterizedType.getRawInterface()});
                }
            } else if (!type.getParameterizedTypes().isEmpty()) {
                List<ParameterizedType> genericTypes = type.getParameterizedTypes();
                if (ModelUtils.isMap(typeName, context) && genericTypes.size() == 2) {
                    createSchema(containerSchema, context, genericTypes.get(0), clazz, classParameterizedTypes);

                    containerSchema = new SchemaImpl();
                    schema.setAdditionalPropertiesSchema(containerSchema);
                    createSchema(containerSchema, context, genericTypes.get(1), clazz, classParameterizedTypes);
                } else {
                    createSchema(containerSchema, context, genericTypes.get(0), clazz, classParameterizedTypes);
                }
            } else {
                return createSchema(containerSchema, context, type);
            }
            return schema;
        }

        return createSchema(schema, context, type);
    }

    private ParameterizedInterfaceModel findParameterizedModelFromGenerics(
            ExtensibleType<? extends ExtensibleType> annotatedElement,
            Collection<ParameterizedInterfaceModel> parameterizedModels,
            ParameterizedType genericType) {
        if (parameterizedModels == null
                || parameterizedModels.isEmpty()) {
            return null;
        }

        List<String> formalParamKeys = new ArrayList<>(annotatedElement.getFormalTypeParameters().keySet());
        int i = 0;
        for (ParameterizedInterfaceModel parameterizedModel : parameterizedModels) {
            if (formalParamKeys.get(i).equals(genericType.getFormalType())) {
                return parameterizedModel;
            }
            i++;
        }
        return null;
    }

    /**
     * Replace the object in the referee with a reference, and create the
     * reference in the API.
     *
     * @param context the API context.
     * @param referee the object containing the reference.
     * @param referenceClass the class of the object being referenced.
     * @return if the reference has been created.
     */
    private boolean insertObjectReference(ApiContext context, Reference<?> referee, AnnotatedElement referenceClass, String referenceClassName) {

        // If the object is a java core class
        if (referenceClassName == null || referenceClassName.startsWith("java.")) {
            return false;
        }

        // If the object is a Java EE object type
        if (referenceClassName.startsWith("javax.")) {
            return false;
        }

        // Check the class exists in the application
        if (!context.isApplicationType(referenceClassName)) {
            return false;
        }

        if (referenceClass != null && referenceClass instanceof ExtensibleType) {
            ExtensibleType referenceClassType = (ExtensibleType) referenceClass;
            final AnnotationModel schemaAnnotation = context.getAnnotationInfo(referenceClassType)
                    .getAnnotation(org.eclipse.microprofile.openapi.annotations.media.Schema.class);
            String schemaName = null;
            if (schemaAnnotation != null) {
                schemaName = schemaAnnotation.getValue("name", String.class);
            }
            if (schemaName == null || schemaName.isEmpty()) {
                schemaName = ModelUtils.getSimpleName(referenceClassName);
            }
            // Set the reference name
            referee.setRef(schemaName);

            Schema schema = context.getApi().getComponents().getSchemas().get(schemaName);
            if (schema == null) {
                // Create the schema
                if (context.isAllowedType(referenceClassType)) {
                    visitSchema(schemaAnnotation, referenceClassType, context);
                } else if (referenceClassType instanceof ClassModel) {
                    apiWalker.processAnnotation((ClassModel) referenceClassType, this);
                } else {
                    LOGGER.log(FINE, "Unrecognised schema {0} class found.", new Object[]{referenceClassName});
                }
            }

            return true;
        }

        return false;
    }

}
