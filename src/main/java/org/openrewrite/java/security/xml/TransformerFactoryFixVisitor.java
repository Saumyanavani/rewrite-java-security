/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.security.xml;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import javax.xml.XMLConstants;

public class TransformerFactoryFixVisitor<P> extends XmlFactoryVisitor<P> {
    static final MethodMatcher TRANSFORMER_FACTORY_INSTANCE = new MethodMatcher("javax.xml.transform.TransformerFactory new*()");
    static final MethodMatcher TRANSFORMER_FACTORY_SET_ATTRIBUTE = new MethodMatcher("javax.xml.transform.TransformerFactory setAttribute(java.lang.String, ..)");
    static final MethodMatcher TRANSFORMER_FACTORY_SET_FEATURE = new MethodMatcher("javax.xml.transform.TransformerFactory setFeature(java.lang.String, ..)");

    private static final String TRANSFORMER_FACTORY_FQN = "javax.xml.transform.TransformerFactory";
    private static final String ACCESS_EXTERNAL_DTD_NAME = "ACCESS_EXTERNAL_DTD";
    private static final String ACCESS_EXTERNAL_STYLESHEET_NAME = "ACCESS_EXTERNAL_STYLESHEET";
    private static final String FEATURE_SECURE_PROCESSING_NAME = "FEATURE_SECURE_PROCESSING";

    private static final String TRANSFORMER_FACTORY_INITIALIZATION_METHOD = "transformer-factory-initialization-method";
    private static final String TRANSFORMER_FACTORY_VARIABLE_NAME = "transformer-factory-variable-name";

    private static final String DISALLOW_MODIFY_FLAG = "DISALLOW_MODIFY_FLAG";

    public TransformerFactoryFixVisitor(ExternalDTDAccumulator acc) {
        super(
                TRANSFORMER_FACTORY_INSTANCE,
                TRANSFORMER_FACTORY_FQN,
                TRANSFORMER_FACTORY_INITIALIZATION_METHOD,
                TRANSFORMER_FACTORY_VARIABLE_NAME,
                acc
        );
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        Cursor supportsExternalCursor = getCursor().getMessage(ACCESS_EXTERNAL_DTD_NAME);
        Cursor supportsStylesheetCursor = getCursor().getMessage(ACCESS_EXTERNAL_STYLESHEET_NAME);
        Cursor supportsFeatureSecureProcessing = getCursor().getMessage(FEATURE_SECURE_PROCESSING_NAME);
        Cursor initializationCursor = getCursor().getMessage(TRANSFORMER_FACTORY_INITIALIZATION_METHOD);
        Cursor disallowModifyFlagCursor = getCursor().getMessage(DISALLOW_MODIFY_FLAG);
        String transformerFactoryVariableName = getCursor().getMessage(TRANSFORMER_FACTORY_VARIABLE_NAME);

        Cursor setAttributeBlockCursor = null;
        if (supportsExternalCursor == null && supportsStylesheetCursor == null && supportsFeatureSecureProcessing == null) {
            setAttributeBlockCursor = initializationCursor;
        } else if ((supportsExternalCursor == null ^ supportsStylesheetCursor == null) || (supportsStylesheetCursor == null ^ supportsFeatureSecureProcessing == null)) {
            if (supportsExternalCursor != null) {
                setAttributeBlockCursor = supportsExternalCursor;
            } else if (supportsStylesheetCursor != null) {
                setAttributeBlockCursor = supportsStylesheetCursor;
            } else {
                setAttributeBlockCursor = supportsFeatureSecureProcessing;
            }
        }
        if (disallowModifyFlagCursor == null && setAttributeBlockCursor != null && transformerFactoryVariableName != null) {
            doAfterVisit(new TransformerFactoryInsertAttributeStatementVisitor<>(
                    setAttributeBlockCursor.getValue(),
                    transformerFactoryVariableName,
                    supportsExternalCursor == null,
                    supportsStylesheetCursor == null,
                    supportsFeatureSecureProcessing == null
            ));
        }
        return cd;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (TRANSFORMER_FACTORY_SET_ATTRIBUTE.matches(m) && m.getArguments().get(0) instanceof J.FieldAccess) {
            // If either attribute value is not equal to the empty string, do not make any changes
            if (m.getArguments().get(1) instanceof J.Literal) {
                J.Literal string = (J.Literal) m.getArguments().get(1);
                assert string.getValue() != null;
                if (!(((String) string.getValue()).isEmpty())) {
                    addMessage(DISALLOW_MODIFY_FLAG);
                }
            }
            J.FieldAccess fa = (J.FieldAccess) m.getArguments().get(0);
            if (ACCESS_EXTERNAL_DTD_NAME.equals(fa.getSimpleName())) {
                addMessage(ACCESS_EXTERNAL_DTD_NAME);
            } else if (ACCESS_EXTERNAL_STYLESHEET_NAME.equals(fa.getSimpleName())) {
                addMessage(ACCESS_EXTERNAL_STYLESHEET_NAME);
            }
        } else if (TRANSFORMER_FACTORY_SET_FEATURE.matches(m)) {
            // If FEATURE_SECURE_PROCESSING is set to false, do not make any changes
            if (m.getArguments().get(1) instanceof J.Literal) {
                J.Literal bool = (J.Literal) m.getArguments().get(1);
                assert bool.getValue() != null;
                if (Boolean.FALSE.equals(bool.getValue())) {
                    addMessage(DISALLOW_MODIFY_FLAG);
                }
            }
            if (m.getArguments().get(0) instanceof J.FieldAccess) {
                J.FieldAccess fa = (J.FieldAccess) m.getArguments().get(0);
                if (FEATURE_SECURE_PROCESSING_NAME.equals(fa.getSimpleName())) {
                    addMessage(FEATURE_SECURE_PROCESSING_NAME);
                }
            } else if (m.getArguments().get(0) instanceof J.Literal) {
                J.Literal literal = (J.Literal) m.getArguments().get(0);
                if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(literal.getValue())) {
                    addMessage(FEATURE_SECURE_PROCESSING_NAME);
                }
            }
        }
        return m;
    }

    public static TreeVisitor<?, ExecutionContext> create(ExternalDTDAccumulator acc) {
        return Preconditions.check(new UsesType<>(TRANSFORMER_FACTORY_FQN, true), new TransformerFactoryFixVisitor<>(acc));
    }
}
