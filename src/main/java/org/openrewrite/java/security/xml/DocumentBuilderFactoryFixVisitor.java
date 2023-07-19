package org.openrewrite.java.security.xml;

import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import javax.xml.stream.XMLInputFactory;

@AllArgsConstructor
public class DocumentBuilderFactoryFixVisitor<P> extends JavaIsoVisitor<P> {

    static final MethodMatcher DBF_NEW_INSTANCE = new MethodMatcher("javax.xml.parsers.DocumentBuilderFactory newInstance*()");
    static final MethodMatcher DBF_PARSER_SET_FEATURE = new MethodMatcher("javax.xml.parsers.DocumentBuilderFactory setFeature(java.lang.String, boolean)");

    private final ExternalDTDAccumulator acc;
    private static final String DBF_FQN = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String DISALLOW_DOCTYPE_DECLARATIONS = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String DBF_INITIALIZATION_METHOD = "dbf-initialization-method";

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (DBF_NEW_INSTANCE.matches(m)) {
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, DBF_INITIALIZATION_METHOD, getCursor().dropParentUntil(J.Block.class::isInstance));
        } else if (DBF_PARSER_SET_FEATURE.matches(m) && m.getArguments().get(0) instanceof J.Identifier) {
            J.Identifier id = (J.Identifier) m.getArguments().get(0);
            if (DISALLOW_DOCTYPE_DECLARATIONS.equals(id.getSimpleName())) {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, DISALLOW_DOCTYPE_DECLARATIONS, getCursor().dropParentUntil(J.Block.class::isInstance));
            }
        }
//        } else if (DBF_PARSER_SET_FEATURE.matches(m) && m.getArguments().get(0) instanceof J.Literal) {
//            J.Literal literal = (J.Literal) m.getArguments().get(0);
//            if (TypeUtils.isString(literal.getType())) {
//                if (XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES.equals(literal.getValue())) {
//                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
//                } else if (XMLInputFactory.SUPPORT_DTD.equals(literal.getValue())) {
//                    checkDTDSupport(m);
//                }
//            }

        return m;
    }

    public static TreeVisitor<?, ExecutionContext> create(ExternalDTDAccumulator acc) {
        return Preconditions.check(new UsesType<>(DBF_FQN, true), new DocumentBuilderFactoryFixVisitor<>(acc));
    }
}
