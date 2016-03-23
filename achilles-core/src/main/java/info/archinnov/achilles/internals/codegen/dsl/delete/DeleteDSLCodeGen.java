/*
 * Copyright (C) 2012-2016 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.archinnov.achilles.internals.codegen.dsl.delete;

import static info.archinnov.achilles.internals.metamodel.columns.ColumnType.*;
import static info.archinnov.achilles.internals.parser.TypeUtils.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import info.archinnov.achilles.internals.codegen.dsl.AbstractDSLCodeGen;
import info.archinnov.achilles.internals.codegen.meta.EntityMetaCodeGen.EntityMetaSignature;
import info.archinnov.achilles.internals.metamodel.columns.ColumnType;
import info.archinnov.achilles.internals.metamodel.columns.PartitionKeyInfo;
import info.archinnov.achilles.internals.parser.FieldParser.FieldMetaSignature;
import info.archinnov.achilles.type.tuples.Tuple2;

public class DeleteDSLCodeGen extends AbstractDSLCodeGen {


    public static Comparator<Tuple2<String, PartitionKeyInfo>> PARTITION_KEY_SORTER =
            (o1, o2) -> o1._2().order.compareTo(o2._2().order);

    public static TypeSpec buildDeleteClass(EntityMetaSignature signature) {

        final String firstPartitionKey = signature.fieldMetaSignatures
                .stream()
                .filter(x -> x.context.columnType == ColumnType.PARTITION)
                .map(x -> Tuple2.of(x.context.fieldName, (PartitionKeyInfo) x.context.columnInfo))
                .sorted(PARTITION_KEY_SORTER)
                .map(Tuple2::_1)
                .findFirst()
                .get();

        TypeName deleteFromTypeName = ClassName.get(DSL_PACKAGE, signature.deleteFromReturnType());

        TypeName deleteColumnsTypeName = ClassName.get(DSL_PACKAGE, signature.deleteColumnsReturnType());

        TypeName deleteWhereTypeName = ClassName.get(DSL_PACKAGE, signature.deleteWhereReturnType(firstPartitionKey));

        final List<ColumnType> candidateColumns = Arrays.asList(NORMAL, STATIC, COUNTER, STATIC_COUNTER);

        final TypeSpec.Builder builder = TypeSpec.classBuilder(signature.deleteClassName())
                .superclass(ABSTRACT_DELETE)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(buildDeleteConstructor(signature))
                .addField(buildExactEntityMetaField(signature))
                .addField(buildEntityClassField(signature))
                .addType(buildDeleteColumns(signature, signature.className + DELETE_COLUMNS_DSL_SUFFIX,
                        deleteColumnsTypeName, deleteFromTypeName, candidateColumns))
                .addType(buildDeleteFrom(signature, signature.className + DELETE_FROM_DSL_SUFFIX,
                        deleteWhereTypeName));

        signature.fieldMetaSignatures
                .stream()
                .filter(x -> candidateColumns.contains(x.context.columnType))
                .forEach(x -> builder.addMethod(buildDeleteColumnMethod(deleteColumnsTypeName, x, ReturnType.NEW)));

        builder.addMethod(buildAllColumns(deleteFromTypeName, DELETE_WHERE, "delete"));
        builder.addMethod(buildAllColumnsWithSchemaProvider(deleteFromTypeName, DELETE_WHERE, "delete"));


        DeleteWhereDSLCodeGen.buildWhereClasses(signature).forEach(builder::addType);

        return builder.build();
    }

    public static TypeSpec buildDeleteStaticClass(EntityMetaSignature signature) {

        final String firstPartitionKey = signature.fieldMetaSignatures
                .stream()
                .filter(x -> x.context.columnType == ColumnType.PARTITION)
                .map(x -> Tuple2.of(x.context.fieldName, (PartitionKeyInfo) x.context.columnInfo))
                .sorted(PARTITION_KEY_SORTER)
                .map(Tuple2::_1)
                .findFirst()
                .get();

        TypeName deleteStaticFromTypeName = ClassName.get(DSL_PACKAGE, signature.deleteStaticFromReturnType());

        TypeName deleteStaticColumnsTypeName = ClassName.get(DSL_PACKAGE, signature.deleteStaticColumnsReturnType());

        TypeName deleteStaticWhereTypeName = ClassName.get(DSL_PACKAGE, signature.deleteStaticWhereReturnType(firstPartitionKey));

        final List<ColumnType> candidateColumns = Arrays.asList(STATIC, STATIC_COUNTER);
        final TypeSpec.Builder builder = TypeSpec.classBuilder(signature.deleteStaticClassName())
                .superclass(ABSTRACT_DELETE)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(buildDeleteConstructor(signature))
                .addField(buildExactEntityMetaField(signature))
                .addField(buildEntityClassField(signature))
                .addType(buildDeleteColumns(signature, signature.className + DELETE_STATIC_COLUMNS_DSL_SUFFIX,
                        deleteStaticColumnsTypeName, deleteStaticFromTypeName, candidateColumns))
                .addType(buildDeleteFrom(signature, signature.className + DELETE_STATIC_FROM_DSL_SUFFIX,
                        deleteStaticWhereTypeName));

        signature.fieldMetaSignatures
                .stream()
                .filter(x -> candidateColumns.contains(x.context.columnType))
                .forEach(x -> builder.addMethod(buildDeleteColumnMethod(deleteStaticColumnsTypeName, x, ReturnType.NEW)));


        DeleteWhereDSLCodeGen.buildWhereClassesForStatic(signature).forEach(builder::addType);

        return builder.build();
    }

    private static MethodSpec buildDeleteConstructor(EntityMetaSignature signature) {
        String metaClassName = signature.className + META_SUFFIX;
        TypeName metaClassType = ClassName.get(ENTITY_META_PACKAGE, metaClassName);

        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(RUNTIME_ENGINE, "rte")
                .addParameter(metaClassType, "meta")
                .addStatement("super(rte)")
                .addStatement("this.meta = meta")
                .build();

    }

    private static TypeSpec buildDeleteColumns(EntityMetaSignature signature,
                                               String deleteColumnClass,
                                               TypeName deleteColumnsTypeName,
                                               TypeName deleteFromTypeName,
                                               List<ColumnType> candidateColumns) {

        final TypeSpec.Builder builder = TypeSpec.classBuilder(deleteColumnClass)
                .superclass(ABSTRACT_DELETE_COLUMNS)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(DELETE_COLUMNS, "deleteColumns")
                        .addStatement("super(deleteColumns)")
                        .build());

        signature.fieldMetaSignatures
                .stream()
                .filter(x -> candidateColumns.contains(x.context.columnType))
                .forEach(x -> builder.addMethod(buildDeleteColumnMethod(deleteColumnsTypeName, x, ReturnType.THIS)));

        builder.addMethod(buildFrom(deleteFromTypeName, DELETE_WHERE, "deleteColumns"));
        builder.addMethod(buildFromWithSchemaProvider(deleteFromTypeName, DELETE_WHERE, "deleteColumns"));

        return builder.build();
    }

    private static TypeSpec buildDeleteFrom(EntityMetaSignature signature,
                                            String deleteFromClassName,
                                            TypeName deleteWhereTypeName) {


        return TypeSpec.classBuilder(deleteFromClassName)
                .superclass(ABSTRACT_DELETE_FROM)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(DELETE_WHERE, "where")
                        .addStatement("super(where)")
                        .build())
                .addMethod(MethodSpec.methodBuilder("where")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addStatement("return new $T(where)", deleteWhereTypeName)
                        .returns(deleteWhereTypeName)
                        .build())
                .build();
    }

    private static MethodSpec buildDeleteColumnMethod(TypeName deleteTypeName, FieldMetaSignature parsingResult, ReturnType returnType) {

        final MethodSpec.Builder builder = MethodSpec.methodBuilder(parsingResult.context.fieldName)
                .addJavadoc("Generate DELETE <strong>$L</strong> ...", parsingResult.context.cqlColumn)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addStatement("delete.column($S)", parsingResult.context.cqlColumn)
                .returns(deleteTypeName);

        if (returnType == ReturnType.NEW) {
            return builder.addStatement("return new $T(delete)", deleteTypeName).build();
        } else {
            return builder.addStatement("return this").build();
        }
    }

}
