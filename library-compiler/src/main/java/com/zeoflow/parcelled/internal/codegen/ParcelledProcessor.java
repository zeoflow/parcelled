// Copyright 2021 ZeoFlow SRL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.zeoflow.parcelled.internal.codegen;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.zeoflow.jx.file.AnnotationSpec;
import com.zeoflow.jx.file.ArrayTypeName;
import com.zeoflow.jx.file.ClassName;
import com.zeoflow.jx.file.CodeBlock;
import com.zeoflow.jx.file.FieldSpec;
import com.zeoflow.jx.file.JavaFile;
import com.zeoflow.jx.file.MethodSpec;
import com.zeoflow.jx.file.NameAllocator;
import com.zeoflow.jx.file.ParameterSpec;
import com.zeoflow.jx.file.ParameterizedTypeName;
import com.zeoflow.jx.file.TypeName;
import com.zeoflow.jx.file.TypeSpec;
import com.zeoflow.parcelled.Default;
import com.zeoflow.parcelled.Parcelled;
import com.zeoflow.parcelled.ParcelledAdapter;
import com.zeoflow.parcelled.ParcelledVersion;
import com.zeoflow.parcelled.internal.common.MoreElements;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@SupportedAnnotationTypes("com.zeoflow.parcelled.Parcelled")
public final class ParcelledProcessor extends AbstractProcessor
{

    private ErrorReporter mErrorReporter;
    private Types mTypeUtils;
    private boolean requiresSuppressWarnings = false;
    private static AnnotationSpec createSuppressUncheckedWarningAnnotation()
    {
        return AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "\"unchecked\"")
                .build();
    }
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv)
    {
        super.init(processingEnv);
        mErrorReporter = new ErrorReporter(processingEnv);
        mTypeUtils = processingEnv.getTypeUtils();
    }
    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latestSupported();
    }
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env)
    {
        Collection<? extends Element> annotatedElements =
                env.getElementsAnnotatedWith(Parcelled.class);
        List<TypeElement> types = new ImmutableList.Builder<TypeElement>()
                .addAll(ElementFilter.typesIn(annotatedElements))
                .build();

        for (TypeElement type : types)
        {
            processType(type);
        }

        // We are the only ones handling Parcelled annotations
        return true;
    }
    private void processType(TypeElement type)
    {
        Parcelled parcelled = type.getAnnotation(Parcelled.class);
        if (parcelled == null)
        {
            mErrorReporter.abortWithError("annotation processor for @Parcelled was invoked with a" +
                    "type annotated differently; compiler bug? O_o", type);
        }
        if (type.getKind() != ElementKind.CLASS)
        {
            mErrorReporter.abortWithError("@" + Parcelled.class.getName() + " only applies to classes", type);
        }
        if (ancestorIsAutoParcel(type))
        {
            mErrorReporter.abortWithError("One @Parcelled class shall not extend another", type);
        }

        checkModifiersIfNested(type);

        // get the fully-qualified interface name
        String fqInterfaceName = generatedInterfaceName(type);
        // interface name
        String interfaceName = TypeUtil.simpleNameOf(fqInterfaceName);

        String sourceInterface = generateInterface(type, interfaceName, type.getSimpleName().toString());
        sourceInterface = Reformatter.fixup(sourceInterface);
        writeSourceFile(fqInterfaceName, sourceInterface, type);

        // get the fully-qualified class name
        String fqClassName = generatedSubclassName(type);
        // class name
        String className = TypeUtil.simpleNameOf(fqClassName);

        String source = generateClass(type, className, interfaceName, type.getSimpleName().toString());
        source = Reformatter.fixup(source);
        writeSourceFile(fqClassName, source, type);

    }
    private void writeSourceFile(String className, String text, TypeElement originatingType)
    {
        try
        {
            JavaFileObject sourceFile =
                    processingEnv.getFiler().createSourceFile(className, originatingType);
            try (Writer writer = sourceFile.openWriter())
            {
                writer.write(text);
            }
        } catch (IOException e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Could not write generated class " + className + ": " + e);
        }
    }

    private String generateClass(TypeElement type, String className, String interfaceName, String classToExtend)
    {
        if (type == null)
        {
            mErrorReporter.abortWithError("generateClass was invoked with null type", null);
        }
        if (className == null)
        {
            mErrorReporter.abortWithError("generateClass was invoked with null class name", type);
        }
        if (classToExtend == null)
        {
            mErrorReporter.abortWithError("generateClass was invoked with null parent class", type);
        }
        assert type != null;
        List<VariableElement> nonPrivateFields = getParcelableFieldsOrError(type);
        if (nonPrivateFields.isEmpty())
        {
            mErrorReporter.abortWithError("generateClass error, all fields are declared PRIVATE", type);
        }

        // get the properties
        ImmutableList<Property> properties = buildProperties(nonPrivateFields);

        // get the type adapters
        ImmutableMap<TypeMirror, FieldSpec> typeAdapters = getTypeAdapters(properties);

        // get the parcel version
        int version = type.getAnnotation(Parcelled.class).version();

        // Generate the Parcelled_$ class
        String pkg = TypeUtil.packageNameOf(type);
        TypeName classTypeName = ClassName.get(pkg, className);
        TypeName interfaceTypeName = ClassName.get(pkg, interfaceName);
        assert className != null;
        // generate writeToParcel()
        TypeSpec.Builder subClass = TypeSpec.classBuilder(className)
                // Add the version
                .addField(TypeName.INT, "version", PRIVATE)
                // Class must be always final
                .addModifiers(FINAL)
                .addSuperinterface(interfaceTypeName)
                // overrides IParcelled_Address
                .addMethod(generateIParcelled(properties))
                // extends from original abstract class
                .superclass(ClassName.get(pkg, classToExtend))
                // Add the AUDO-DEFAULT constructor
                .addMethod(generateAutoConstructor(properties))
                // Add the DEFAULT constructor
                .addMethod(generateConstructor(properties))
                // Add the private constructor
                .addMethod(generateConstructorFromParcel(processingEnv, properties, typeAdapters))
                // overrides describeContents()
                .addMethod(generateDescribeContents())
                // static final CREATOR
                .addField(generateCreator(classTypeName))
                // overrides writeToParcel()
                .addMethod(generateWriteToParcel(version, processingEnv, properties, typeAdapters));

        if (!ancestoIsParcelable(processingEnv, type))
        {
            // Implement android.os.Parcelable if the ancestor does not do it.
            subClass.addSuperinterface(ClassName.get("android.os", "Parcelable"));
        }

        if (!typeAdapters.isEmpty())
        {
            typeAdapters.values().forEach(subClass::addField);
        }

        JavaFile javaFile = JavaFile.builder(pkg, subClass.build()).build();
        return javaFile.toString();
    }

    private String generateInterface(TypeElement type, String className, String classToExtend)
    {
        if (type == null)
        {
            mErrorReporter.abortWithError("generateClass was invoked with null type", null);
        }
        if (className == null)
        {
            mErrorReporter.abortWithError("generateClass was invoked with null class name", type);
        }
        if (classToExtend == null)
        {
            mErrorReporter.abortWithError("generateClass was invoked with null parent class", type);
        }
        assert type != null;
        List<VariableElement> nonPrivateFields = getParcelableFieldsOrError(type);
        if (nonPrivateFields.isEmpty())
        {
            mErrorReporter.abortWithError("generateClass error, all fields are declared PRIVATE", type);
        }

        // get the properties
        ImmutableList<Property> properties = buildProperties(nonPrivateFields);

        // Generate the Parcelled_$ class
        assert className != null;
        // generate writeToParcel()
        TypeSpec.Builder subClass = TypeSpec.interfaceBuilder(className)
                // Add the private constructor
                .addModifiers(PUBLIC)
                .addMethod(generateInterfaceSet(properties));

        String pkg = TypeUtil.packageNameOf(type);
        JavaFile javaFile = JavaFile.builder(pkg, subClass.build()).build();
        return javaFile.toString();
    }

    private MethodSpec generateInterfaceSet(ImmutableList<Property> properties)
    {

        MethodSpec.Builder builder = MethodSpec.methodBuilder("setValues")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
        builder.addJavadoc("Values Setter");
        builder.addJavadoc("\n");

        List<ParameterSpec> params = Lists.newArrayListWithCapacity(properties.size());
        for (Property property : properties)
        {
            params.add(ParameterSpec.builder(property.typeName, property.fieldName).build());
        }
        for (ParameterSpec param : params)
        {
            builder.addJavadoc("\n@param " + param.name + " {@link " + param.type + "}");
            builder.addParameter(param.type, param.name);
        }

        return builder.build();
    }

    private ImmutableMap<TypeMirror, FieldSpec> getTypeAdapters(ImmutableList<Property> properties)
    {
        Map<TypeMirror, FieldSpec> typeAdapters = new LinkedHashMap<>();
        NameAllocator nameAllocator = new NameAllocator();
        nameAllocator.newName("CREATOR");
        for (Property property : properties)
        {
            if (property.typeAdapter != null && !typeAdapters.containsKey(property.typeAdapter))
            {
                ClassName typeName = (ClassName) TypeName.get(property.typeAdapter);
                String name = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, typeName.simpleName());
                name = nameAllocator.newName(name, typeName);

                typeAdapters.put(property.typeAdapter, FieldSpec.builder(
                        typeName, NameAllocator.toJavaIdentifier(name), PRIVATE, STATIC, FINAL)
                        .initializer("new $T()", typeName).build());
            }
        }
        return ImmutableMap.copyOf(typeAdapters);
    }
    private ImmutableList<Property> buildProperties(List<VariableElement> elements)
    {
        ImmutableList.Builder<Property> builder = ImmutableList.builder();
        for (VariableElement element : elements)
        {
            builder.add(new Property(element.getSimpleName().toString(), element));
        }

        return builder.build();
    }
    /**
     * This method returns a list of all non private fields. If any <code>private</code> fields is
     * found, the method errors out
     *
     * @param type element
     *
     * @return list of all non-<code>private</code> fields
     */
    private List<VariableElement> getParcelableFieldsOrError(TypeElement type)
    {
        List<VariableElement> allFields = ElementFilter.fieldsIn(type.getEnclosedElements());
        List<VariableElement> nonPrivateFields = new ArrayList<>();

        for (VariableElement field : allFields)
        {
            if (!field.getModifiers().contains(PRIVATE))
            {
                nonPrivateFields.add(field);
            } else
            {
                // return error, PRIVATE fields are not allowed
                mErrorReporter.abortWithError("getFieldsError error, PRIVATE fields not allowed", type);
            }
        }

        return nonPrivateFields;
    }

    private MethodSpec generateConstructor(ImmutableList<Property> properties)
    {

        List<ParameterSpec> params = Lists.newArrayListWithCapacity(properties.size());
        for (Property property : properties)
        {
            params.add(ParameterSpec.builder(property.typeName, property.fieldName).build());
        }

        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addParameters(params);
        builder.addJavadoc("Constructor");
        builder.addJavadoc("\n");
        for (ParameterSpec param : params)
        {
            builder.addJavadoc("\n@param " + param.name + " {@link " + param.type + "}");
            builder.addStatement("this.$N = $N", param.name, param.name);
        }

        return builder.build();
    }

    private MethodSpec generateAutoConstructor(ImmutableList<Property> properties)
    {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addJavadoc("Auto Constructor");
        builder.addJavadoc("\n");

        List<ParameterSpec> params = Lists.newArrayListWithCapacity(properties.size());
        for (Property property : properties)
        {
            params.add(ParameterSpec.builder(property.typeName, property.fieldName).build());
        }
        for (int i=0; i<params.size(); i++)
        {
            ParameterSpec param = params.get(i);
            builder.addJavadoc("\n@param " + param.name + " {@link " + param.type + "}");
            if (!properties.get(i).getDefaultCode().equals(""))
            {
                builder.addStatement("this.$N = $N", param.name, properties.get(i).getDefaultCode());
            } else
            {
                builder.addStatement("this.$N = $N", param.name, param.name);
            }
        }

        return builder.build();
    }

    private MethodSpec generateConstructorFromParcel(
            ProcessingEnvironment env,
            ImmutableList<Property> properties,
            ImmutableMap<TypeMirror, FieldSpec> typeAdapters)
    {

        // Create the PRIVATE constructor from Parcel
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(PRIVATE)      // private
                .addParameter(ClassName.bestGuess("android.os.Parcel"), "in"); // input param

        builder.addJavadoc("Parcelable builder");
        builder.addJavadoc("\n");
        builder.addJavadoc("\n@param in {@link " + ClassName.bestGuess("android.os.Parcel") + "}");

        // get a code block builder
        CodeBlock.Builder block = CodeBlock.builder();

        // First thing is reading the Parcelable object version
        block.add("this.version = in.readInt();\n");

        // Now, iterate all properties, check the version initialize them
        for (Property p : properties)
        {

            // get the property version
            int aVersion = p.getAfterVersion();
            int bVersion = p.getBeforeVersion();
            if (aVersion > 0 && bVersion > 0)
            {
                block.beginControlFlow("if (this.version >= $L && this.version <= $L)", aVersion, bVersion);
            } else if (aVersion > 0)
            {
                block.beginControlFlow("if (this.version >= $L)", aVersion);
            } else if (bVersion > 0)
            {
                block.beginControlFlow("if (this.version <= $L)", bVersion);
            }

            block.add("this.$N = ", p.fieldName);

            if (p.typeAdapter != null && typeAdapters.containsKey(p.typeAdapter))
            {
                Parcelables.readValueWithTypeAdapter(block, p, typeAdapters.get(p.typeAdapter));
            } else
            {
                requiresSuppressWarnings |= Parcelables.isTypeRequiresSuppressWarnings(p.typeName);
                TypeName parcelableType = Parcelables.getTypeNameFromProperty(p, env.getTypeUtils());
                Parcelables.readValue(block, p, parcelableType);
            }

            block.add(";\n");

            if (aVersion > 0 || bVersion > 0)
            {
                block.endControlFlow();
            }
        }

        builder.addCode(block.build());

        return builder.build();
    }

    private String generatedSubclassName(TypeElement type)
    {
        String classNameSuffix = "Parcelled_";
        return generatedClassName(type, Strings.repeat("$", 0) + classNameSuffix);
    }

    private String generatedInterfaceName(TypeElement type)
    {
        String classNameSuffix = "IParcelled_";
        return generatedClassName(type, Strings.repeat("$", 0) + classNameSuffix);
    }

    private String generatedClassName(TypeElement type, String prefix)
    {
        StringBuilder name = new StringBuilder(type.getSimpleName().toString());
        while (type.getEnclosingElement() instanceof TypeElement)
        {
            type = (TypeElement) type.getEnclosingElement();
            name.insert(0, type.getSimpleName() + "_");
        }
        String pkg = TypeUtil.packageNameOf(type);
        String dot = pkg.isEmpty() ? "" : ".";
        return pkg + dot + prefix + name;
    }

    private MethodSpec generateWriteToParcel(
            int version,
            ProcessingEnvironment env,
            ImmutableList<Property> properties,
            ImmutableMap<TypeMirror, FieldSpec> typeAdapters)
    {
        ParameterSpec dest = ParameterSpec
                .builder(ClassName.get("android.os", "Parcel"), "dest")
                .build();
        ParameterSpec flags = ParameterSpec.builder(int.class, "flags").build();
        MethodSpec.Builder builder = MethodSpec.methodBuilder("writeToParcel")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(dest)
                .addParameter(flags);

        // write first the parcelable object version...
        builder.addCode(Parcelables.writeVersion(version, dest));

        // ...then write all the properties
        for (Property p : properties)
        {
            if (p.typeAdapter != null && typeAdapters.containsKey(p.typeAdapter))
            {
                FieldSpec typeAdapter = typeAdapters.get(p.typeAdapter);
                builder.addCode(Parcelables.writeValueWithTypeAdapter(typeAdapter, p, dest));
            } else
            {
                builder.addCode(Parcelables.writeValue(p, dest, flags, env.getTypeUtils()));
            }
        }

        return builder.build();
    }

    private MethodSpec generateDescribeContents()
    {
        return MethodSpec.methodBuilder("describeContents")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return 0")
                .build();
    }

    private MethodSpec generateIParcelled(ImmutableList<Property> properties)
    {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("setValues")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC);

        List<ParameterSpec> params = Lists.newArrayListWithCapacity(properties.size());
        for (Property property : properties)
        {
            params.add(ParameterSpec.builder(property.typeName, property.fieldName).build());
        }
        for (ParameterSpec param : params)
        {
            builder.addJavadoc("\n@param " + param.name + " {@link " + param.type + "}");
            builder.addParameter(param.type, param.name);
            builder.addStatement("this.$N = $N", param.name, param.name);
        }

        return builder.build();
    }

    private FieldSpec generateCreator(TypeName type)
    {
        ClassName creator = ClassName.bestGuess("android.os.Parcelable.Creator");
        TypeName creatorOfClass = ParameterizedTypeName.get(creator, type);

        CodeBlock.Builder ctorCall = CodeBlock.builder();
        ctorCall.add("return new $T(in);\n", type);

        // Method createFromParcel()
        MethodSpec.Builder createFromParcel = MethodSpec.methodBuilder("createFromParcel")
                .addAnnotation(Override.class);
        if (requiresSuppressWarnings)
        {
            createFromParcel.addAnnotation(createSuppressUncheckedWarningAnnotation());
        }
        createFromParcel
                .addModifiers(PUBLIC)
                .returns(type)
                .addParameter(ClassName.bestGuess("android.os.Parcel"), "in");
        createFromParcel.addCode(ctorCall.build());

        TypeSpec creatorImpl = TypeSpec.anonymousClassBuilder("")
                .superclass(creatorOfClass)
                .addMethod(createFromParcel
                        .build())
                .addMethod(MethodSpec.methodBuilder("newArray")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(ArrayTypeName.of(type))
                        .addParameter(int.class, "size")
                        .addStatement("return new $T[size]", type)
                        .build())
                .build();

        return FieldSpec
                .builder(creatorOfClass, "CREATOR", PUBLIC, FINAL, STATIC)
                .initializer("$L", creatorImpl)
                .build();
    }

    private void checkModifiersIfNested(TypeElement type)
    {
        ElementKind enclosingKind = type.getEnclosingElement().getKind();
        if (enclosingKind.isClass() || enclosingKind.isInterface())
        {
            if (type.getModifiers().contains(PRIVATE))
            {
                mErrorReporter.abortWithError("@Parcelled class must not be private", type);
            }
            if (!type.getModifiers().contains(STATIC))
            {
                mErrorReporter.abortWithError("Nested @Parcelled class must be static", type);
            }
        }
        // In principle type.getEnclosingElement() could be an ExecutableElement (for a class
        // declared inside a method), but since RoundEnvironment.getElementsAnnotatedWith doesn't
        // return such classes we won't see them here.
    }

    private boolean ancestorIsAutoParcel(TypeElement type)
    {
        while (true)
        {
            TypeMirror parentMirror = type.getSuperclass();
            if (parentMirror.getKind() == TypeKind.NONE)
            {
                return false;
            }
            TypeElement parentElement = (TypeElement) mTypeUtils.asElement(parentMirror);
            if (MoreElements.isAnnotationPresent(parentElement, Parcelled.class))
            {
                return true;
            }
            type = parentElement;
        }
    }

    private boolean ancestoIsParcelable(ProcessingEnvironment env, TypeElement type)
    {
        TypeMirror classType = type.asType();
        TypeMirror parcelable = env.getElementUtils().getTypeElement("android.os.Parcelable").asType();
        return TypeUtil.isClassOfType(env.getTypeUtils(), parcelable, classType);
    }

    static final class Property
    {

        final String fieldName;
        final VariableElement element;
        final TypeName typeName;
        final ImmutableSet<String> annotations;
        String defaultCode = "";
        final int version;
        final int afterVersion;
        final int beforeVersion;
        TypeMirror typeAdapter;

        Property(String fieldName, VariableElement element)
        {
            this.fieldName = fieldName;
            this.element = element;
            this.typeName = TypeName.get(element.asType());
            this.annotations = getAnnotations(element);

            // get the parcel adapter if any
            ParcelledAdapter parcelledAdapter = element.getAnnotation(ParcelledAdapter.class);
            if (parcelledAdapter != null)
            {
                try
                {
                    parcelledAdapter.value();
                } catch (MirroredTypeException e)
                {
                    this.typeAdapter = e.getTypeMirror();
                }

            }

            element.getConstantValue();
            Default defaultCode = element.getAnnotation(Default.class);
            this.defaultCode = defaultCode == null ? "" : defaultCode.code();

            // get the element version, default 0
            ParcelledVersion parcelledVersion = element.getAnnotation(ParcelledVersion.class);
            this.version = parcelledVersion == null ? 0 : parcelledVersion.after();
            this.afterVersion = parcelledVersion == null ? 0 : parcelledVersion.after();
            this.beforeVersion = parcelledVersion == null ? 0 : parcelledVersion.before();
        }

        public boolean isNullable()
        {
            return this.annotations.contains("Nullable");
        }

        public int getAfterVersion()
        {
            return this.afterVersion;
        }

        public int getBeforeVersion()
        {
            return this.beforeVersion;
        }

        public String getDefaultCode()
        {
            return this.defaultCode;
        }

        private ImmutableSet<String> getAnnotations(VariableElement element)
        {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for (AnnotationMirror annotation : element.getAnnotationMirrors())
            {
                builder.add(annotation.getAnnotationType().asElement().getSimpleName().toString());
            }

            return builder.build();
        }

    }

}
