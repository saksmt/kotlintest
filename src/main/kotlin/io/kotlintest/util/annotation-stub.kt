package io.kotlintest.util

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.reflect.Array as ReflectArray

inline fun <reified T : Annotation> a(valueArg: Any?): T = createAnnotationStub(
    { it?.name == "value" },
    { me, method, args -> valueArg }
)

inline fun <reified T : Annotation> a(args: Map<String, Any?>): T = createAnnotationStub(
    { args.containsKey(it?.name) },
    { me, method, args_ -> args[method?.name] }
)

inline fun <reified T : Annotation> a(vararg args: Pair<String, Any?> = emptyArray()): T = a(mapOf(*args))

inline fun <reified T : Annotation> createAnnotationStub(
    crossinline handledCase: (Method?) -> Boolean,
    crossinline handler: (T, Method?, Array<Any?>?) -> Any?
): T {
  return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args: Array<Any?>? ->
    val self = proxy as T
    handleCommonAnnotationMethods(self, method, args) ?:
        if (handledCase(method))
          handler(self, method, args)
        else
          extractDefaultsIfAny(method)
  } as T
}

fun extractDefaultsIfAny(method: Method): Any? {
  return if (method.defaultValue != null) {
    method.defaultValue
  } else if (method.returnType.isArray) {
    java.lang.reflect.Array.newInstance(method.returnType.componentType, 0)
  } else {
    null
  }
}

inline fun <reified T : Annotation> handleCommonAnnotationMethods(instance: T, currentMethod: Method?, args: Array<Any?>?)
    = when (currentMethod?.name) {
  "annotationType" -> T::class.java
  "equals" -> {
    val compareTo = args?.firstOrNull()
    compareTo != null && compareTo is Annotation && extractAnnotationValues(instance).toList() == extractAnnotationValues(compareTo).toList()
  }
  "toString" -> "@" + T::class.java.canonicalName + "(" + extractAnnotationValues(instance).map { it.first + "=" + it.second }.joinToString(", ") + ")"
  "hashCode" -> 0L
  else -> null
}

fun extractAnnotationValues(annotation: Annotation): Sequence<Pair<String, Any?>> {
  return annotation.annotationClass.java.declaredMethods.asSequence()
      .filter { it.name !in arrayOf("equals", "hashCode", "toString", "annotationType") }
      .filter { it.parameters.isEmpty() }
      .map({ it.name to it.invoke(annotation) })
}