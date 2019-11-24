package com.stefanolupo.processors;

import com.google.auto.service.AutoService;
import com.stefanolupo.annotations.Factory;
import com.stefanolupo.models.FactoryAnnotatedClass;
import com.stefanolupo.models.FactoryGroupedClasses;
import com.stefanolupo.models.IdAlreadyUsedException;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class);
public class FactoryProcessor extends AbstractProcessor {
    private Types typeUtils;
    private Elements elementsUtils;
    private Filer filer;
    private Messager messager;

    // Store the group of factory classes indexed by the qualified name of the class they all share (e.g. Meal)
    private Map<String, FactoryGroupedClasses> factoryGroupsByName = new LinkedHashMap<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(Factory.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementsUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element annotatedElement : roundEnvironment.getElementsAnnotatedWith(Factory.class)) {
            if (annotatedElement.getKind() != ElementKind.CLASS) {
                error(annotatedElement, "Only classes can be annotated with @%s", Factory.class.getSimpleName());
                return true;
            }

            TypeElement typeElement = (TypeElement) annotatedElement;

            try {
                // Throws illegal argument exception
                FactoryAnnotatedClass factoryAnnotatedClass = new FactoryAnnotatedClass(typeElement);

                if (!isValidClass(factoryAnnotatedClass)) {
                    return true;
                }

                FactoryGroupedClasses factoryGroup = factoryGroupsByName.get(factoryAnnotatedClass.getQualifiedFactoryGroupName());
                if (factoryGroup == null) {
                    String qualifiedGroupName = factoryAnnotatedClass.getQualifiedFactoryGroupName();
                    factoryGroup = new FactoryGroupedClasses(qualifiedGroupName);
                    factoryGroupsByName.put(qualifiedGroupName, factoryGroup);
                }

                factoryGroup.add(factoryAnnotatedClass);

            } catch (IllegalArgumentException e) {
                // Factory id was empty
                error(typeElement, e.getMessage());
                return true;
            } catch (IdAlreadyUsedException e) {
                FactoryAnnotatedClass existingClass = e.getExisting();
                error(annotatedElement,
                        "Conflict: The class %s is annotated with @%s with id ='%s' but %s already uses the same id",
                        typeElement.getQualifiedName().toString(), Factory.class.getSimpleName(),
                        existingClass.getTypeElement().getQualifiedName().toString());
                return true;
            }

            try {
                for (FactoryGroupedClasses factoryClass : factoryGroupsByName.values()) {
                    factoryClass.generateCode(elementsUtils, filer);
                }
            } catch (IOException e) {
                error(null, e.getMessage());
            }

            return true;
        }



        return false;
    }

    private boolean isValidClass(FactoryAnnotatedClass factoryAnnotatedClass) {
        TypeElement classElement = factoryAnnotatedClass.getTypeElement();

        if (!classElement.getModifiers().contains(Modifier.PUBLIC)) {
            error(classElement, "The class %s is not public.", classElement.getQualifiedName().toString());
            return false;
        }


        if (classElement.getModifiers().contains(Modifier.ABSTRACT)) {
            error(classElement, "The class %s is abstract", classElement.getQualifiedName().toString());
            return false;
        }

        TypeElement superClassElement = elementsUtils.getTypeElement(factoryAnnotatedClass.getQualifiedFactoryGroupName());
        if (superClassElement.getKind() == ElementKind.INTERFACE) {
            error(classElement, "The class %s annotated with @%s must implement the interface %s",
                    classElement.getQualifiedName().toString(), Factory.class.getSimpleName(), factoryAnnotatedClass.getQualifiedFactoryGroupName());
            return false;
        }

        // Check subclassing
        TypeElement currentClass = classElement;
        while (true) {
            TypeMirror superClassType = currentClass.getSuperclass();

            if (superClassType.getKind() == TypeKind.NONE) {
                // Basis class (java.lang.Object) reached, so exit
                error(classElement, "The class %s annotated with @%s must inherit from %s",
                        classElement.getQualifiedName().toString(), Factory.class.getSimpleName(),
                        factoryAnnotatedClass.getQualifiedFactoryGroupName());
                return false;
            }

            if (superClassType.toString().equals(factoryAnnotatedClass.getQualifiedFactoryGroupName())) {
                // Required super class found
                break;
            }

            // Moving up in inheritance tree
            currentClass = (TypeElement) typeUtils.asElement(superClassType);
        }

        // Check if an empty public constructor is given
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }

            ExecutableElement constructorElement = (ExecutableElement) enclosed;
            if (constructorElement.getParameters().size() == 0 && constructorElement.getModifiers().contains(Modifier.PUBLIC)) {
                // Found an empty constructor
                return true;
            }
        }

        // No empty constructor found
        error(classElement, "The class %s must provide an public empty default constructor",
                classElement.getQualifiedName().toString());
        return false;
    }

    private void error(Element element, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), element);
    }
}
