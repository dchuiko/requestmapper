package com.viartemev.requestmapper.annotations.spring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.viartemev.requestmapper.RequestMappingItem;
import com.viartemev.requestmapper.annotations.MappingAnnotation;
import com.viartemev.requestmapper.annotations.spring.extraction.BasePsiAnnotationValueVisitor;
import com.viartemev.requestmapper.annotations.spring.extraction.PsiAnnotationMemberValueExtractor;
import com.viartemev.requestmapper.annotations.spring.extraction.PsiArrayInitializerMemberValueExtractor;
import com.viartemev.requestmapper.annotations.spring.extraction.PsiReferenceExpressionExtractor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.viartemev.requestmapper.utils.CommonUtils.unquote;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.EMPTY;

public class RequestMapping implements MappingAnnotation {
    private static final String VALUE_PARAM = "value";
    private static final String PATH_PARAM = "path";
    private static final String METHOD_PARAM = "method";
    private static final String SPRING_REQUEST_MAPPING_CLASS = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String DEFAULT_METHOD = "GET";

    final PsiAnnotation psiAnnotation;
    final PsiElement psiElement;
    final Project project;

    public RequestMapping(PsiAnnotation psiAnnotation,
                          PsiElement psiElement,
                          Project project) {
        this.psiAnnotation = psiAnnotation;
        this.psiElement = psiElement;
        this.project = project;
    }

    @Override
    public List<RequestMappingItem> values() {
        if (psiElement instanceof PsiMethod) {
            return fetchRequestMappingItem(psiAnnotation, (PsiMethod) psiElement, fetchMethodFromAnnotation(psiAnnotation, METHOD_PARAM));
        } else if (psiElement instanceof PsiClass) {
            return fetchRequestMappingItem(psiAnnotation, (PsiClass) psiElement);
        }
        return Collections.emptyList();
    }

    List<RequestMappingItem> fetchRequestMappingItem(PsiAnnotation annotation, PsiMethod psiMethod, String method) {
        List<String> classMappings = new ArrayList<>();
        for (PsiAnnotation requestMappingAnnotation : fetchRequestMappingAnnotationsFromParentClass(psiMethod)) {
            classMappings.addAll(fetchMapping(requestMappingAnnotation));
        }

        List<String> methodMappings = fetchMapping(annotation);
        List<RequestMappingItem> result = new ArrayList<>();
        for (String url : methodMappings) {
            if (classMappings.size() != 0) {
                for (String classValue : classMappings) {
                    result.add(new RequestMappingItem(psiMethod, classValue + url, method));
                }
            } else {
                result.add(new RequestMappingItem(psiMethod, url, method));
            }
        }
        return result;
    }

    @NotNull
    private PsiAnnotation[] fetchRequestMappingAnnotationsFromParentClass(PsiMethod psiMethod) {
        List<PsiAnnotation> requestMappingAnnotations = new ArrayList<>();
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.getModifierList() != null) {
            PsiAnnotation[] annotations = containingClass.getModifierList().getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                if (annotation != null && Objects.equals(annotation.getQualifiedName(), SPRING_REQUEST_MAPPING_CLASS)) {
                    requestMappingAnnotations.add(annotation);
                }
            }
        }
        return requestMappingAnnotations.toArray(new PsiAnnotation[]{});
    }

    private String fetchMethodFromAnnotation(PsiAnnotation annotation, String parameter) {
        PsiAnnotationMemberValue valueParam = annotation.findAttributeValue(parameter);
        if (valueParam != null && StringUtils.isNotEmpty(valueParam.getText()) && !Objects.equals("{}", valueParam.getText())) {
            return valueParam.getText().replace("RequestMethod.", "");
        }
        return DEFAULT_METHOD;
    }

    private List<RequestMappingItem> fetchRequestMappingItem(PsiAnnotation annotation, PsiClass psiClass) {
        List<String> classMappings = fetchMapping(annotation);
        return classMappings.stream().map(url -> new RequestMappingItem(psiClass, unquote(url), EMPTY)).collect(toList());
    }

    private List<String> fetchMapping(PsiAnnotation annotation) {
        List<String> pathMapping = fetchMappingsFromAnnotation(annotation, PATH_PARAM);
        if (!pathMapping.isEmpty()) {
            return pathMapping;
        }
        return fetchMappingsFromAnnotation(annotation, VALUE_PARAM);
    }

    private List<String> fetchMappingsFromAnnotation(PsiAnnotation annotation, String parameter) {
        return new BasePsiAnnotationValueVisitor() {
            @Override
            public List<String> visitPsiArrayInitializerMemberValue(@NotNull PsiArrayInitializerMemberValue memberValue) {
                return new PsiArrayInitializerMemberValueExtractor().extract(memberValue);
            }

            @Override
            public List<String> visitPsiReferenceExpression(@NotNull PsiReferenceExpression expression) {
                return new PsiReferenceExpressionExtractor(project).extract(expression);
            }

            @Override
            public List<String> visitPsiAnnotationMemberValue(@NotNull PsiAnnotationMemberValue value) {
                return new PsiAnnotationMemberValueExtractor().extract(value);
            }
        }.visit(annotation, parameter);
    }
}
