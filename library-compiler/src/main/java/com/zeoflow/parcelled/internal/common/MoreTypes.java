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

package com.zeoflow.parcelled.internal.common;

import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.WILDCARD;

/**
 * Utilities related to {@link TypeMirror} instances.
 */
public final class MoreTypes
{

    private static final Class<?> INTERSECTION_TYPE;
    private static final Method GET_BOUNDS;
    private static final TypeVisitor<Boolean, EqualVisitorParam> EQUAL_VISITOR =
            new SimpleTypeVisitor6<Boolean, EqualVisitorParam>()
            {
                @Override
                protected Boolean defaultAction(TypeMirror a, EqualVisitorParam p)
                {
                    return a.getKind().equals(p.type.getKind());
                }

                @Override
                public Boolean visitArray(ArrayType a, EqualVisitorParam p)
                {
                    if (p.type.getKind().equals(ARRAY))
                    {
                        ArrayType b = (ArrayType) p.type;
                        return equal(a.getComponentType(), b.getComponentType(), p.visiting);
                    }
                    return false;
                }

                @Override
                public Boolean visitDeclared(DeclaredType a, EqualVisitorParam p)
                {
                    if (p.type.getKind().equals(DECLARED))
                    {
                        DeclaredType b = (DeclaredType) p.type;
                        Element aElement = a.asElement();
                        Element bElement = b.asElement();
                        Set<ComparedElements> newVisiting = visitingSetPlus(
                                p.visiting, aElement, a.getTypeArguments(), bElement, b.getTypeArguments());
                        if (newVisiting.equals(p.visiting))
                        {
                            // We're already visiting this pair of elements.
                            // This can happen for example with Enum in Enum<E extends Enum<E>>. Return a
                            // provisional true value since if the Elements are not in fact equal the original
                            // visitor of Enum will discover that. We have to check both Elements being compared
                            // though to avoid missing the fact that one of the types being compared
                            // differs at exactly this point.
                            return true;
                        }
                        return aElement.equals(bElement)
                                && equal(a.getEnclosingType(), a.getEnclosingType(), newVisiting)
                                && equalLists(a.getTypeArguments(), b.getTypeArguments(), newVisiting);

                    }
                    return false;
                }

                @Override
                public Boolean visitError(ErrorType a, EqualVisitorParam p)
                {
                    return a.equals(p.type);
                }

                @Override
                public Boolean visitExecutable(ExecutableType a, EqualVisitorParam p)
                {
                    if (p.type.getKind().equals(EXECUTABLE))
                    {
                        ExecutableType b = (ExecutableType) p.type;
                        return equalLists(a.getParameterTypes(), b.getParameterTypes(), p.visiting)
                                && equal(a.getReturnType(), b.getReturnType(), p.visiting)
                                && equalLists(a.getThrownTypes(), b.getThrownTypes(), p.visiting)
                                && equalLists(a.getTypeVariables(), b.getTypeVariables(), p.visiting);
                    }
                    return false;
                }

                @Override
                public Boolean visitTypeVariable(TypeVariable a, EqualVisitorParam p)
                {
                    if (p.type.getKind().equals(TYPEVAR))
                    {
                        TypeVariable b = (TypeVariable) p.type;
                        TypeParameterElement aElement = (TypeParameterElement) a.asElement();
                        TypeParameterElement bElement = (TypeParameterElement) b.asElement();
                        Set<ComparedElements> newVisiting = visitingSetPlus(p.visiting, aElement, bElement);
                        if (newVisiting.equals(p.visiting))
                        {
                            // We're already visiting this pair of elements.
                            // This can happen with our friend Eclipse when looking at <T extends Comparable<T>>.
                            // It incorrectly reports the upper bound of T as T itself.
                            return true;
                        }
                        // We use aElement.getBounds() instead of a.getUpperBound() to avoid having to deal with
                        // the different way intersection types (like <T extends Number & Comparable<T>>) are
                        // represented before and after Java 8. We do have an issue that this code may consider
                        // that <T extends Foo & Bar> is different from <T extends Bar & Foo>, but it's very
                        // hard to avoid that, and not likely to be much of a problem in practice.
                        return equalLists(aElement.getBounds(), bElement.getBounds(), newVisiting)
                                && equal(a.getLowerBound(), b.getLowerBound(), newVisiting)
                                && a.asElement().getSimpleName().equals(b.asElement().getSimpleName());
                    }
                    return false;
                }

                @Override
                public Boolean visitWildcard(WildcardType a, EqualVisitorParam p)
                {
                    if (p.type.getKind().equals(WILDCARD))
                    {
                        WildcardType b = (WildcardType) p.type;
                        return equal(a.getExtendsBound(), b.getExtendsBound(), p.visiting)
                                && equal(a.getSuperBound(), b.getSuperBound(), p.visiting);
                    }
                    return false;
                }

                @Override
                public Boolean visitUnknown(TypeMirror a, EqualVisitorParam p)
                {
                    throw new UnsupportedOperationException();
                }

                private Set<ComparedElements> visitingSetPlus(
                        Set<ComparedElements> visiting, Element a, Element b)
                {
                    ImmutableList<TypeMirror> noArguments = ImmutableList.of();
                    return visitingSetPlus(visiting, a, noArguments, b, noArguments);
                }

                private Set<ComparedElements> visitingSetPlus(
                        Set<ComparedElements> visiting,
                        Element a, List<? extends TypeMirror> aArguments,
                        Element b, List<? extends TypeMirror> bArguments)
                {
                    ComparedElements comparedElements =
                            new ComparedElements(
                                    a, ImmutableList.<TypeMirror>copyOf(aArguments),
                                    b, ImmutableList.<TypeMirror>copyOf(bArguments));
                    Set<ComparedElements> newVisiting = new HashSet<ComparedElements>(visiting);
                    newVisiting.add(comparedElements);
                    return newVisiting;
                }
            };
    private static final int HASH_SEED = 17;
    private static final int HASH_MULTIPLIER = 31;
    private static final TypeVisitor<Integer, Set<Element>> HASH_VISITOR =
            new SimpleTypeVisitor6<Integer, Set<Element>>()
            {
                int hashKind(int seed, TypeMirror t)
                {
                    int result = seed * HASH_MULTIPLIER;
                    result += t.getKind().hashCode();
                    return result;
                }

                @Override
                protected Integer defaultAction(TypeMirror e, Set<Element> visiting)
                {
                    return hashKind(HASH_SEED, e);
                }

                @Override
                public Integer visitArray(ArrayType t, Set<Element> visiting)
                {
                    int result = hashKind(HASH_SEED, t);
                    result *= HASH_MULTIPLIER;
                    result += t.getComponentType().accept(this, visiting);
                    return result;
                }

                @Override
                public Integer visitDeclared(DeclaredType t, Set<Element> visiting)
                {
                    Element element = t.asElement();
                    if (visiting.contains(element))
                    {
                        return 0;
                    }
                    Set<Element> newVisiting = new HashSet<Element>(visiting);
                    newVisiting.add(element);
                    int result = hashKind(HASH_SEED, t);
                    result *= HASH_MULTIPLIER;
                    result += t.asElement().hashCode();
                    result *= HASH_MULTIPLIER;
                    result += t.getEnclosingType().accept(this, newVisiting);
                    result *= HASH_MULTIPLIER;
                    result += hashList(t.getTypeArguments(), newVisiting);
                    return result;
                }

                @Override
                public Integer visitExecutable(ExecutableType t, Set<Element> visiting)
                {
                    int result = hashKind(HASH_SEED, t);
                    result *= HASH_MULTIPLIER;
                    result += hashList(t.getParameterTypes(), visiting);
                    result *= HASH_MULTIPLIER;
                    result += t.getReturnType().accept(this, visiting);
                    result *= HASH_MULTIPLIER;
                    result += hashList(t.getThrownTypes(), visiting);
                    result *= HASH_MULTIPLIER;
                    result += hashList(t.getTypeVariables(), visiting);
                    return result;
                }

                @Override
                public Integer visitTypeVariable(TypeVariable t, Set<Element> visiting)
                {
                    int result = hashKind(HASH_SEED, t);
                    result *= HASH_MULTIPLIER;
                    result += t.getLowerBound().accept(this, visiting);
                    TypeParameterElement element = (TypeParameterElement) t.asElement();
                    for (TypeMirror bound : element.getBounds())
                    {
                        result *= HASH_MULTIPLIER;
                        result += bound.accept(this, visiting);
                    }
                    return result;
                }

                @Override
                public Integer visitWildcard(WildcardType t, Set<Element> visiting)
                {
                    int result = hashKind(HASH_SEED, t);
                    result *= HASH_MULTIPLIER;
                    result +=
                            (t.getExtendsBound() == null) ? 0 : t.getExtendsBound().accept(this, visiting);
                    result *= HASH_MULTIPLIER;
                    result += (t.getSuperBound() == null) ? 0 : t.getSuperBound().accept(this, visiting);
                    return result;
                }

                @Override
                public Integer visitUnknown(TypeMirror t, Set<Element> visiting)
                {
                    throw new UnsupportedOperationException();
                }
            };
    private static final Equivalence<TypeMirror> TYPE_EQUIVALENCE = new Equivalence<TypeMirror>()
    {
        @Override
        protected boolean doEquivalent(TypeMirror a, TypeMirror b)
        {
            return MoreTypes.equal(a, b, ImmutableSet.<ComparedElements>of());
        }

        @Override
        protected int doHash(TypeMirror t)
        {
            return MoreTypes.hash(t, ImmutableSet.<Element>of());
        }
    };
    private static final TypeVisitor<Element, Void> AS_ELEMENT_VISITOR =
            new SimpleTypeVisitor6<Element, Void>()
            {
                @Override
                protected Element defaultAction(TypeMirror e, Void p)
                {
                    throw new IllegalArgumentException(e + "cannot be converted to an Element");
                }

                @Override
                public Element visitDeclared(DeclaredType t, Void p)
                {
                    return t.asElement();
                }

                @Override
                public Element visitError(ErrorType t, Void p)
                {
                    return t.asElement();
                }

                @Override
                public Element visitTypeVariable(TypeVariable t, Void p)
                {
                    return t.asElement();
                }
            };
    static
    {
        Class<?> c;
        Method m;
        try
        {
            c = Class.forName("javax.lang.model.type.IntersectionType");
            m = c.getMethod("getBounds");
        } catch (Exception e)
        {
            c = null;
            m = null;
        }
        INTERSECTION_TYPE = c;
        GET_BOUNDS = m;
    }

    private MoreTypes()
    {
    }
    public static Equivalence<TypeMirror> equivalence()
    {
        return TYPE_EQUIVALENCE;
    }
    private static boolean equal(TypeMirror a, TypeMirror b, Set<ComparedElements> visiting)
    {
        // TypeMirror.equals is not guaranteed to return true for types that are equal, but we can
        // assume that if it does return true then the types are equal. This check also avoids getting
        // stuck in infinite recursion when Eclipse decrees that the upper bound of the second K in
        // <K extends Comparable<K>> is a distinct but equal K.
        // The javac implementation of ExecutableType, at least in some versions, does not take thrown
        // exceptions into account in its equals implementation, so avoid this optimization for
        // ExecutableType.
        if (Objects.equal(a, b) && !(a instanceof ExecutableType))
        {
            return true;
        }
        EqualVisitorParam p = new EqualVisitorParam();
        p.type = b;
        p.visiting = visiting;
        if (INTERSECTION_TYPE != null)
        {
            if (isIntersectionType(a))
            {
                return equalIntersectionTypes(a, b, visiting);
            } else if (isIntersectionType(b))
            {
                return false;
            }
        }
        return (a == b) || (a != null && b != null && a.accept(EQUAL_VISITOR, p));
    }
    private static boolean isIntersectionType(TypeMirror t)
    {
        return t != null && t.getKind().name().equals("INTERSECTION");
    }
    // The representation of an intersection type, as in <T extends Number & Comparable<T>>, changed
    // between Java 7 and Java 8. In Java 7 it was modeled as a fake DeclaredType, and our logic
    // for DeclaredType does the right thing. In Java 8 it is modeled as a new type IntersectionType.
    // In order for our code to run on Java 7 (and Java 6) we can't even mention IntersectionType,
    // so we can't override visitIntersectionType(IntersectionType). Instead, we discover through
    // reflection whether IntersectionType exists, and if it does we extract the bounds of the
    // intersection ((Number, Comparable<T>) in the example) and compare them directly.
    @SuppressWarnings("unchecked")
    private static boolean equalIntersectionTypes(
            TypeMirror a, TypeMirror b, Set<ComparedElements> visiting)
    {
        if (!isIntersectionType(b))
        {
            return false;
        }
        List<? extends TypeMirror> aBounds;
        List<? extends TypeMirror> bBounds;
        try
        {
            aBounds = (List<? extends TypeMirror>) GET_BOUNDS.invoke(a);
            bBounds = (List<? extends TypeMirror>) GET_BOUNDS.invoke(b);
        } catch (Exception e)
        {
            throw Throwables.propagate(e);
        }
        return equalLists(aBounds, bBounds, visiting);
    }
    private static boolean equalLists(
            List<? extends TypeMirror> a, List<? extends TypeMirror> b,
            Set<ComparedElements> visiting)
    {
        int size = a.size();
        if (size != b.size())
        {
            return false;
        }
        // Use iterators in case the Lists aren't RandomAccess
        Iterator<? extends TypeMirror> aIterator = a.iterator();
        Iterator<? extends TypeMirror> bIterator = b.iterator();
        while (aIterator.hasNext())
        {
            if (!bIterator.hasNext())
            {
                return false;
            }
            TypeMirror nextMirrorA = aIterator.next();
            TypeMirror nextMirrorB = bIterator.next();
            if (!equal(nextMirrorA, nextMirrorB, visiting))
            {
                return false;
            }
        }
        return !aIterator.hasNext();
    }
    private static int hashList(List<? extends TypeMirror> mirrors, Set<Element> visiting)
    {
        int result = HASH_SEED;
        for (TypeMirror mirror : mirrors)
        {
            result *= HASH_MULTIPLIER;
            result += hash(mirror, visiting);
        }
        return result;
    }

    private static int hash(TypeMirror mirror, Set<Element> visiting)
    {
        return mirror == null ? 0 : mirror.accept(HASH_VISITOR, visiting);
    }

    /**
     * Returns the set of {@linkplain TypeElement types} that are referenced by the given
     * {@link TypeMirror}.
     */
    public static ImmutableSet<TypeElement> referencedTypes(TypeMirror type)
    {
        checkNotNull(type);
        ImmutableSet.Builder<TypeElement> elements = ImmutableSet.builder();
        type.accept(new SimpleTypeVisitor6<Void, ImmutableSet.Builder<TypeElement>>()
        {
            @Override
            public Void visitArray(ArrayType t, ImmutableSet.Builder<TypeElement> p)
            {
                t.getComponentType().accept(this, p);
                return null;
            }

            @Override
            public Void visitDeclared(DeclaredType t, ImmutableSet.Builder<TypeElement> p)
            {
                p.add(MoreElements.asType(t.asElement()));
                for (TypeMirror typeArgument : t.getTypeArguments())
                {
                    typeArgument.accept(this, p);
                }
                return null;
            }

            @Override
            public Void visitTypeVariable(TypeVariable t, ImmutableSet.Builder<TypeElement> p)
            {
                t.getLowerBound().accept(this, p);
                t.getUpperBound().accept(this, p);
                return null;
            }

            @Override
            public Void visitWildcard(WildcardType t, ImmutableSet.Builder<TypeElement> p)
            {
                TypeMirror extendsBound = t.getExtendsBound();
                if (extendsBound != null)
                {
                    extendsBound.accept(this, p);
                }
                TypeMirror superBound = t.getSuperBound();
                if (superBound != null)
                {
                    superBound.accept(this, p);
                }
                return null;
            }
        }, elements);
        return elements.build();
    }

    /**
     * An alternate implementation of {@link Types#asElement} that does not require a {@link Types}
     * instance with the notable difference that it will throw {@link IllegalArgumentException}
     * instead of returning null if the {@link TypeMirror} can not be converted to an {@link Element}.
     *
     * @throws NullPointerException     if {@code typeMirror} is {@code null}
     * @throws IllegalArgumentException if {@code typeMirror} cannot be converted to an
     *                                  {@link Element}
     */
    public static Element asElement(TypeMirror typeMirror)
    {
        return typeMirror.accept(AS_ELEMENT_VISITOR, null);
    }
    // TODO(gak): consider removing these two methods as they're pretty trivial now
    public static TypeElement asTypeElement(TypeMirror mirror)
    {
        return MoreElements.asType(asElement(mirror));
    }
    public static ImmutableSet<TypeElement> asTypeElements(Iterable<? extends TypeMirror> mirrors)
    {
        checkNotNull(mirrors);
        ImmutableSet.Builder<TypeElement> builder = ImmutableSet.builder();
        for (TypeMirror mirror : mirrors)
        {
            builder.add(asTypeElement(mirror));
        }
        return builder.build();
    }
    /**
     * Returns a {@link ArrayType} if the {@link TypeMirror} represents a primitive array or
     * throws an {@link IllegalArgumentException}.
     */
    public static ArrayType asArray(TypeMirror maybeArrayType)
    {
        return maybeArrayType.accept(new CastingTypeVisitor<ArrayType>()
        {
            @Override
            public ArrayType visitArray(ArrayType type, String ignore)
            {
                return type;
            }
        }, "primitive array");
    }
    /**
     * Returns a {@link DeclaredType} if the {@link TypeMirror} represents a declared type such
     * as a class, interface, union/compound, or enum or throws an {@link IllegalArgumentException}.
     */
    public static DeclaredType asDeclared(TypeMirror maybeDeclaredType)
    {
        return maybeDeclaredType.accept(new CastingTypeVisitor<DeclaredType>()
        {
            @Override
            public DeclaredType visitDeclared(DeclaredType type, String ignored)
            {
                return type;
            }
        }, "declared type");
    }
    /**
     * Returns a {@link ExecutableType} if the {@link TypeMirror} represents an executable type such
     * as may result from missing code, or bad compiles or throws an {@link IllegalArgumentException}.
     */
    public static ErrorType asError(TypeMirror maybeErrorType)
    {
        return maybeErrorType.accept(new CastingTypeVisitor<ErrorType>()
        {
            @Override
            public ErrorType visitError(ErrorType type, String p)
            {
                return type;
            }
        }, "error type");
    }
    /**
     * Returns a {@link ExecutableType} if the {@link TypeMirror} represents an executable type such
     * as a method, constructor, or initializer or throws an {@link IllegalArgumentException}.
     */
    public static ExecutableType asExecutable(TypeMirror maybeExecutableType)
    {
        return maybeExecutableType.accept(new CastingTypeVisitor<ExecutableType>()
        {
            @Override
            public ExecutableType visitExecutable(ExecutableType type, String p)
            {
                return type;
            }
        }, "executable type");
    }
    /**
     * Returns a {@link NoType} if the {@link TypeMirror} represents an non-type such
     * as void, or package, etc. or throws an {@link IllegalArgumentException}.
     */
    public static NoType asNoType(TypeMirror maybeNoType)
    {
        return maybeNoType.accept(new CastingTypeVisitor<NoType>()
        {
            @Override
            public NoType visitNoType(NoType noType, String p)
            {
                return noType;
            }
        }, "non-type");
    }
    /**
     * Returns a {@link NullType} if the {@link TypeMirror} represents the null type
     * or throws an {@link IllegalArgumentException}.
     */
    public static NullType asNullType(TypeMirror maybeNullType)
    {
        return maybeNullType.accept(new CastingTypeVisitor<NullType>()
        {
            @Override
            public NullType visitNull(NullType nullType, String p)
            {
                return nullType;
            }
        }, "null");
    }
    /**
     * Returns a {@link PrimitiveType} if the {@link TypeMirror} represents a primitive type
     * or throws an {@link IllegalArgumentException}.
     */
    public static PrimitiveType asPrimitiveType(TypeMirror maybePrimitiveType)
    {
        return maybePrimitiveType.accept(new CastingTypeVisitor<PrimitiveType>()
        {
            @Override
            public PrimitiveType visitPrimitive(PrimitiveType type, String p)
            {
                return type;
            }
        }, "primitive type");
    }
    /**
     * Returns a {@link TypeVariable} if the {@link TypeMirror} represents a type variable
     * or throws an {@link IllegalArgumentException}.
     */
    public static TypeVariable asTypeVariable(TypeMirror maybeTypeVariable)
    {
        return maybeTypeVariable.accept(new CastingTypeVisitor<TypeVariable>()
        {
            @Override
            public TypeVariable visitTypeVariable(TypeVariable type, String p)
            {
                return type;
            }
        }, "type variable");
    }

    //
    // visitUnionType would go here, but it is a 1.7 API.
    //
    /**
     * Returns a {@link WildcardType} if the {@link TypeMirror} represents a wildcard type
     * or throws an {@link IllegalArgumentException}.
     */
    public static WildcardType asWildcard(WildcardType maybeWildcardType)
    {
        return maybeWildcardType.accept(new CastingTypeVisitor<WildcardType>()
        {
            @Override
            public WildcardType visitWildcard(WildcardType type, String p)
            {
                return type;
            }
        }, "wildcard type");
    }
    /**
     * Returns true if the raw type underlying the given {@link TypeMirror} represents a type that can
     * be referenced by a {@link Class}. If this returns true, then {@link #isTypeOf} is guaranteed to
     * not throw.
     */
    public static boolean isType(TypeMirror type)
    {
        return type.accept(new SimpleTypeVisitor6<Boolean, Void>()
        {
            @Override
            protected Boolean defaultAction(TypeMirror type, Void ignored)
            {
                return false;
            }

            @Override
            public Boolean visitNoType(NoType noType, Void p)
            {
                return noType.getKind().equals(TypeKind.VOID);
            }

            @Override
            public Boolean visitPrimitive(PrimitiveType type, Void p)
            {
                return true;
            }

            @Override
            public Boolean visitArray(ArrayType array, Void p)
            {
                return true;
            }

            @Override
            public Boolean visitDeclared(DeclaredType type, Void ignored)
            {
                return MoreElements.isType(type.asElement());
            }
        }, null);
    }
    /**
     * Returns true if the raw type underlying the given {@link TypeMirror} represents the
     * same raw type as the given {@link Class} and throws an IllegalArgumentException if the
     * {@link TypeMirror} does not represent a type that can be referenced by a {@link Class}
     */
    public static boolean isTypeOf(final Class<?> clazz, TypeMirror type)
    {
        checkNotNull(clazz);
        return type.accept(new SimpleTypeVisitor6<Boolean, Void>()
        {
            @Override
            protected Boolean defaultAction(TypeMirror type, Void ignored)
            {
                throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
            }

            @Override
            public Boolean visitNoType(NoType noType, Void p)
            {
                if (noType.getKind().equals(TypeKind.VOID))
                {
                    return clazz.equals(Void.TYPE);
                }
                throw new IllegalArgumentException(noType + " cannot be represented as a Class<?>.");
            }

            @Override
            public Boolean visitPrimitive(PrimitiveType type, Void p)
            {
                switch (type.getKind())
                {
                    case BOOLEAN:
                        return clazz.equals(Boolean.TYPE);
                    case BYTE:
                        return clazz.equals(Byte.TYPE);
                    case CHAR:
                        return clazz.equals(Character.TYPE);
                    case DOUBLE:
                        return clazz.equals(Double.TYPE);
                    case FLOAT:
                        return clazz.equals(Float.TYPE);
                    case INT:
                        return clazz.equals(Integer.TYPE);
                    case LONG:
                        return clazz.equals(Long.TYPE);
                    case SHORT:
                        return clazz.equals(Short.TYPE);
                    default:
                        throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
                }
            }

            @Override
            public Boolean visitArray(ArrayType array, Void p)
            {
                return clazz.isArray()
                        && isTypeOf(clazz.getComponentType(), array.getComponentType());
            }

            @Override
            public Boolean visitDeclared(DeclaredType type, Void ignored)
            {
                TypeElement typeElement;
                try
                {
                    typeElement = MoreElements.asType(type.asElement());
                } catch (IllegalArgumentException iae)
                {
                    throw new IllegalArgumentException(type + " does not represent a class or interface.");
                }
                return typeElement.getQualifiedName().contentEquals(clazz.getCanonicalName());
            }
        }, null);
    }
    /**
     * Returns the non-object superclass of the type with the proper type parameters.
     * An absent Optional is returned if there is no non-Object superclass.
     */
    public static Optional<DeclaredType> nonObjectSuperclass(final Types types, Elements elements,
                                                             DeclaredType type)
    {
        checkNotNull(types);
        checkNotNull(elements);
        checkNotNull(type);

        final TypeMirror objectType =
                elements.getTypeElement(Object.class.getCanonicalName()).asType();
        // It's guaranteed there's only a single CLASS superclass because java doesn't have multiple
        // class inheritance.
        TypeMirror superclass = getOnlyElement(FluentIterable.from(types.directSupertypes(type))
                .filter(new Predicate<TypeMirror>()
                {
                    @Override
                    public boolean apply(TypeMirror input)
                    {
                        return input.getKind().equals(TypeKind.DECLARED)
                                && (MoreElements.asType(
                                MoreTypes.asDeclared(input).asElement())).getKind().equals(ElementKind.CLASS)
                                && !types.isSameType(objectType, input);
                    }
                }), null);
        return superclass != null
                ? Optional.of(MoreTypes.asDeclared(superclass))
                : Optional.<DeclaredType>absent();
    }
    /**
     * Resolves a {@link VariableElement} parameter to a method or constructor based on the given
     * container, or a member of a class. For parameters to a method or constructor, the variable's
     * enclosing element must be a supertype of the container type. For example, given a
     * {@code container} of type {@code Set<String>}, and a variable corresponding to the {@code E e}
     * parameter in the {@code Set.add(E e)} method, this will return a TypeMirror for {@code String}.
     */
    public static TypeMirror asMemberOf(Types types, DeclaredType container,
                                        VariableElement variable)
    {
        if (variable.getKind().equals(ElementKind.PARAMETER))
        {
            ExecutableElement methodOrConstructor =
                    MoreElements.asExecutable(variable.getEnclosingElement());
            ExecutableType resolvedMethodOrConstructor = MoreTypes.asExecutable(
                    types.asMemberOf(container, methodOrConstructor));
            List<? extends VariableElement> parameters = methodOrConstructor.getParameters();
            List<? extends TypeMirror> parameterTypes =
                    resolvedMethodOrConstructor.getParameterTypes();
            checkState(parameters.size() == parameterTypes.size());
            for (int i = 0; i < parameters.size(); i++)
            {
                // We need to capture the parameter type of the variable we're concerned about,
                // for later printing.  This is the only way to do it since we can't use
                // types.asMemberOf on variables of methods.
                if (parameters.get(i).equals(variable))
                {
                    return parameterTypes.get(i);
                }
            }
            throw new IllegalStateException("Could not find variable: " + variable);
        } else
        {
            return types.asMemberOf(container, variable);
        }
    }

    // So EQUAL_VISITOR can be a singleton, we maintain visiting state, in particular which types
    // have been seen already, in this object.
    // The logic for handling recursive types like Comparable<T extends Comparable<T>> is very tricky.
    // If we're not careful we'll end up with an infinite recursion. So we record the types that
    // we've already seen during the recursion, and if we see the same pair of types again we just
    // return true provisionally. But "the same pair of types" is itself poorly-defined. We can't
    // just say that it is an equal pair of TypeMirrors, because of course if we knew how to
    // determine that then we wouldn't need the complicated type visitor at all. On the other hand,
    // we can't say that it is an identical pair of TypeMirrors either, because there's no
    // guarantee that the TypeMirrors for the two Ts in Comparable<T extends Comparable<T>> will be
    // represented by the same object, and indeed with the Eclipse compiler they aren't. We could
    // compare the corresponding Elements, since equality is well-defined there, but that's not enough
    // either, because the Element for Set<Object> is the same as the one for Set<String>. So we
    // approximate by comparing the Elements and, if there are any type arguments, requiring them to
    // be identical. This may not be foolproof either but it is sufficient for all the cases we've
    // encountered so far.
    private static final class EqualVisitorParam
    {

        TypeMirror type;
        Set<ComparedElements> visiting;

    }

    private static class ComparedElements
    {

        final Element a;
        final ImmutableList<TypeMirror> aArguments;
        final Element b;
        final ImmutableList<TypeMirror> bArguments;

        ComparedElements(
                Element a,
                ImmutableList<TypeMirror> aArguments,
                Element b,
                ImmutableList<TypeMirror> bArguments)
        {
            this.a = a;
            this.aArguments = aArguments;
            this.b = b;
            this.bArguments = bArguments;
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof ComparedElements)
            {
                ComparedElements that = (ComparedElements) o;
                int nArguments = aArguments.size();
                if (!this.a.equals(that.a)
                        || !this.b.equals(that.b)
                        || nArguments != bArguments.size())
                {
                    // The arguments must be the same size, but we check anyway.
                    return false;
                }
                for (int i = 0; i < nArguments; i++)
                {
                    if (aArguments.get(i) != bArguments.get(i))
                    {
                        return false;
                    }
                }
                return true;
            } else
            {
                return false;
            }
        }

        @Override
        public int hashCode()
        {
            return a.hashCode() * 31 + b.hashCode();
        }

    }

    private static class CastingTypeVisitor<T> extends SimpleTypeVisitor6<T, String>
    {

        @Override
        protected T defaultAction(TypeMirror e, String label)
        {
            throw new IllegalArgumentException(e + " does not represent a " + label);
        }

    }

}