package com.vesperin.kiwi.api

import com.vesperin.kiwi.domain._
import org.specs2.mutable.Specification
import spray.http.{HttpEntity, MediaTypes}
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._

/**
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
class PostKiwiCleanupApiSpec extends Specification with Specs2RouteTest with Kiwi {
  def actorRefFactory = system

  // reason of this addition? see https://groups.google.com/forum/#!msg/spray-user/o8DtI6VUMbA/n9tguTb_1noJ
  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))

  "Kiwi" should {
    "Return a cleanup request for POST requests to the root path" in {
      Post("/kiwi/eval?auth_token=legolas", Command(cleanup = Some(Cleanup(Code(name = "ToSort.java", description = "Name class", content = "import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.List;\nimport java.util.*;\n\npublic class ToSort implements Comparable {\n\n    private Float val;\n    private String id;\n\n    public ToSort(Float val, String id) {\n      this.val = val;\n      this.id = id;\n    }\n\n    @Override\n    public int compareTo(Object o) {\n\n      ToSort f = (ToSort) o;\n\n      if (val.floatValue() > f.val.floatValue()) {\n        return 1;\n      } else if (val.floatValue() < f.val.floatValue()) {\n        return -1;\n      } else {\n        return 0;\n      }\n\n    }\n\n    @Override\n    public String toString() {\n      return this.id;\n    }\n  }"))))) ~>
        sealRoute(routes) ~> check {

        responseAs[Result].draft.get.before === Code(name = "ToSort.java", description = "Name class", content = "import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.List;\nimport java.util.*;\n\npublic class ToSort implements Comparable {\n\n    private Float val;\n    private String id;\n\n    public ToSort(Float val, String id) {\n      this.val = val;\n      this.id = id;\n    }\n\n    @Override\n    public int compareTo(Object o) {\n\n      ToSort f = (ToSort) o;\n\n      if (val.floatValue() > f.val.floatValue()) {\n        return 1;\n      } else if (val.floatValue() < f.val.floatValue()) {\n        return -1;\n      } else {\n        return 0;\n      }\n\n    }\n\n    @Override\n    public String toString() {\n      return this.id;\n    }\n  }")
        responseAs[Result].draft.get.after  === Code(name = "ToSort.java", description = "Name class", content = "import java.util.*;\n\npublic class ToSort implements Comparable {\n\n  private Float val;\n  private String id;\n\n  public ToSort(Float val, String id) {\n    this.val = val;\n    this.id = id;\n  }\n\n  @Override\n  public int compareTo(Object o) {\n\n    ToSort f = (ToSort) o;\n\n    if (val.floatValue() > f.val.floatValue()) {\n      return 1;\n    } else if (val.floatValue() < f.val.floatValue()) {\n      return -1;\n    } else {\n      return 0;\n    }\n\n  }\n\n  @Override\n  public String toString() {\n    return this.id;\n  }\n}")
      }
    }

    "Return a cleanup request for POST requests to the root path" in {
      Post("/kiwi/eval?auth_token=legolas", Command(cleanup = Some(Cleanup(Code(name = "Name.java", description = "Name class", content = "class Name {\n\t/** {@link Name#boom(String)} **/\tvoid boom(){ System.out.println(1); }\n\tvoid baam(){ System.out.println(1); }\n\tvoid beem(){ System.out.println(1); }\n\tvoid buum(){ baam(); }\n}"))))) ~>
        sealRoute(routes) ~> check {
        responseAs[Result].draft.get.before === Code(name = "Name.java", description = "Name class", content = "class Name {\n\t/** {@link Name#boom(String)} **/\tvoid boom(){ System.out.println(1); }\n\tvoid baam(){ System.out.println(1); }\n\tvoid beem(){ System.out.println(1); }\n\tvoid buum(){ baam(); }\n}")
        responseAs[Result].draft.get.after  === Code(name = "Name.java", description = "Name class", content = "class Name {\n  /** {@link Name#boom(String)} **/\n  void boom() {\n    System.out.println(1);\n  }\n\n  void buum() {\n    boom();\n  }\n}")
      }
    }

    "Return a cleanup request in JSON form for POST requests to the root path" in {
      Post("/kiwi/eval?auth_token=legolas", HttpEntity(MediaTypes.`application/json`, """{"cleanup": { "source": {"name": "Name.java", "description":"Name class", "content":"class Name {\n\t/** {@link Name#boom(String)} **/\tvoid boom(){ System.out.println(1); }\n\tvoid baam(){ System.out.println(1); }\n\tvoid beem(){ System.out.println(1); }\n\tvoid buum(){ baam(); }\n}", "tags":[], "datastructures": [], "algorithms": [], "refactorings": [], "confidence": 2, "comments":[]}, "preprocess": false }}""" )) ~>
        sealRoute(routes) ~> check {
        responseAs[Result].draft.get.before === Code(name = "Name.java", description = "Name class", content = "class Name {\n\t/** {@link Name#boom(String)} **/\tvoid boom(){ System.out.println(1); }\n\tvoid baam(){ System.out.println(1); }\n\tvoid beem(){ System.out.println(1); }\n\tvoid buum(){ baam(); }\n}")
        responseAs[Result].draft.get.after  === Code(name = "Name.java", description = "Name class", content = "class Name {\n  /** {@link Name#boom(String)} **/\n  void boom() {\n    System.out.println(1);\n  }\n\n  void buum() {\n    boom();\n  }\n}")
      }
    }


    "Return a cleanup request (with preprocessing) in JSON form for POST requests to the root path" in {
      Post("/kiwi/eval?auth_token=legolas", HttpEntity(MediaTypes.`application/json`, """{"cleanup": { "source": {"name": "Scratched.java", "description":"Scratched class", "content":"/** {@link Name#boom(String)} **/\tvoid boom(){ System.out.println(1); }\n\tvoid baam(){ System.out.println(1); }\n\tvoid beem(){ System.out.println(1); }\n\tvoid buum(){ baam(); }", "tags":[], "datastructures": [], "algorithms": [], "refactorings": [], "confidence": 2, "comments":[]}, "preprocess":true }}""" )) ~>
        sealRoute(routes) ~> check {
        responseAs[Result].draft.get.before mustEqual Code(name = "Scratched.java", description = "Scratched class", content = "/** {@link Name#boom(String)} **/\tvoid boom(){ System.out.println(1); }\n\tvoid baam(){ System.out.println(1); }\n\tvoid beem(){ System.out.println(1); }\n\tvoid buum(){ baam(); }")
        responseAs[Result].draft.get.after  mustEqual Code(name = "Scratched.java", description = "Scratched class", content = "/** {@link Name#boom(String)} **/\n  void boom() {\n    System.out.println(1);\n  }\n\n  void buum() {\n    boom();\n  }")
      }
    }

    "Return a cleanup request (with preprocessing 2) in JSON form for POST requests to the root path" in {
      Post("/kiwi/eval?auth_token=legolas", HttpEntity(MediaTypes.`application/json`, """{"cleanup": { "source": {"name": "Scratched.java", "description":"Scratched class", "content":"public static void quicksort(int[] a, int p, int r)\n    {\n        int q;\n        while (p < r)\n        {\n            q = partition(a, p, r);\n            quicksort(a, p, q - 1);\n            p = q + 1;\n        }\n    }\n\n    public static int partition(int[] a, int p, int r)\n    {\n        int j = p - 1;\n        int x = a[r];\n        for (int i = p; i < r; i++)\n        {\n            if (a[i] <= x)\n            {\n                j++;\n                swap(a, i, j);\n            }\n        }\n        j++;\n        swap(a, j, r);\n        return j;\n    }\n\n    private static void swap(int[] a, int i, int j)\n    {\n        int tmp = a[i];\n        a[i] = a[j];\n        a[j] = tmp;\n    }", "tags":[], "datastructures": [], "algorithms": [], "refactorings": [], "confidence": 2, "comments":[]}, "preprocess":true }}""" )) ~>
        sealRoute(routes) ~> check {
        responseAs[Result].draft.get.before mustEqual Code(name = "Scratched.java", description = "Scratched class", content = "public static void quicksort(int[] a, int p, int r)\n    {\n        int q;\n        while (p < r)\n        {\n            q = partition(a, p, r);\n            quicksort(a, p, q - 1);\n            p = q + 1;\n        }\n    }\n\n    public static int partition(int[] a, int p, int r)\n    {\n        int j = p - 1;\n        int x = a[r];\n        for (int i = p; i < r; i++)\n        {\n            if (a[i] <= x)\n            {\n                j++;\n                swap(a, i, j);\n            }\n        }\n        j++;\n        swap(a, j, r);\n        return j;\n    }\n\n    private static void swap(int[] a, int i, int j)\n    {\n        int tmp = a[i];\n        a[i] = a[j];\n        a[j] = tmp;\n    }")
        responseAs[Result].draft.get.after  mustEqual Code(name = "Scratched.java", description = "Scratched class", content = "public static void quicksort(int[] a, int p, int r) {\n    int q;\n    while (p < r) {\n      q = partition(a, p, r);\n      quicksort(a, p, q - 1);\n      p = q + 1;\n    }\n  }\n\n  public static int partition(int[] a, int p, int r) {\n    int j = p - 1;\n    int x = a[r];\n    for (int i = p; i < r; i++) {\n      if (a[i] <= x) {\n        j++;\n        swap(a, i, j);\n      }\n    }\n    j++;\n    swap(a, j, r);\n    return j;\n  }\n\n  private static void swap(int[] a, int i, int j) {\n    int tmp = a[i];\n    a[i] = a[j];\n    a[j] = tmp;\n  }")
      }
    }

    "Return a cleanup request (optimize) in JSON form for POST requests to the root path" in {
      Post("/kiwi/eval?auth_token=legolas", HttpEntity(MediaTypes.`application/json`, """{"cleanup": { "source": {"name": "Quicksort.java", "description":"Quicksort algorithm", "content":"import java.util.Random;\n\npublic class Quicksort {\n  private static int partition(int[] arr, int left, int right) {\n    int pivot = arr[left];\n    int i = left - 1;\n    int j = right + 1;\n    while (true) {\n      do {\n        j--;\n      } while (arr[j] > pivot);\n\n      do {\n        i++;\n      } while (arr[i] < pivot);\n\n      if (i < j) {\n        swap(arr, i, j);\n      } else {\n        return j;\n      }\n    }\n  }\n\n  private static void swap(int[] arr, int i, int j) {\n    int tmp = arr[i];\n    arr[i] = arr[j];\n    arr[j] = tmp;\n  }\n\n\n\n}", "tags":[], "datastructures": [], "algorithms": [], "refactorings": [], "confidence": 2, "comments":[]}, "preprocess": false }}""" )) ~>
        sealRoute(routes) ~> check {
        responseAs[Result].draft.get.before === Code(name = "Quicksort.java", description = "Quicksort algorithm", content = "import java.util.Random;\n\npublic class Quicksort {\n  private static int partition(int[] arr, int left, int right) {\n    int pivot = arr[left];\n    int i = left - 1;\n    int j = right + 1;\n    while (true) {\n      do {\n        j--;\n      } while (arr[j] > pivot);\n\n      do {\n        i++;\n      } while (arr[i] < pivot);\n\n      if (i < j) {\n        swap(arr, i, j);\n      } else {\n        return j;\n      }\n    }\n  }\n\n  private static void swap(int[] arr, int i, int j) {\n    int tmp = arr[i];\n    arr[i] = arr[j];\n    arr[j] = tmp;\n  }\n\n\n\n}")
        responseAs[Result].draft.get.after  === Code(name = "Quicksort.java", description = "Quicksort algorithm", content = "public class Quicksort {\n  private static int partition(int[] arr, int left, int right) {\n    int pivot = arr[left];\n    int i = left - 1;\n    int j = right + 1;\n    while (true) {\n      do {\n        j--;\n      } while (arr[j] > pivot);\n\n      do {\n        i++;\n      } while (arr[i] < pivot);\n\n      if (i < j) {\n        swap(arr, i, j);\n      } else {\n        return j;\n      }\n    }\n  }\n\n  private static void swap(int[] arr, int i, int j) {\n    int tmp = arr[i];\n    arr[i] = arr[j];\n    arr[j] = tmp;\n  }\n\n\n\n}")
      }
    }

  }

}
