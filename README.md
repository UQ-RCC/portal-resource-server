# Resource Server

The "resource server" is the service that talks to the HPC. It can mint SSH certificates
to impersonate any (non-operator) user.

Source Code: [GitHub](https://github.com/UQ-RCC/portal-resource-server) (https://github.com/UQ-RCC/portal-resource-server)

The resource server provides several endpoints:

- `/api/execute/{task}`
- `/api/execute/{task}/on/{host}`
- `/api/execute/{task}/in/{configuration}`
- `/api/execute/{task}/in/{configuration}/on/{host}`
- `/api/configurations`

The only one ever used by the portals is `/api/execute/{task}`. The resource server is based
off the old Strudel Web code, so they must have had some prior purpose.

## Configuration

Tasks are defined via a separate JSON configuration file. See the short example below.

```json
[
  ["Nimrod"],
  {
    "Nimrod": {
      "loginHost": null,
      "username": null,
      "messageRegexs": [
        { "pattern": "^INFO:(?P<info>.*(?:\n|\r\n?))" },
        { "pattern": "^WARN:(?P<warn>.*(?:\n|\r\n?))" },
        { "pattern": "^ERROR:(?P<error>.*(?:\n|\r\n?))" }
      ],
      "Commands": {
        "echoX": {
          "async": false,
          "cmd": "echo {x}",
          "failFatal": true,
          "formatFatal": false,
          "host": "login",
          "loop": false,
          "regex": [null],
          "requireMatch": false
        },
        "getAssignments": {
          "async": false,
          "exec": {
            "command": ["/opt/nimrod-portal/bin/shimrod.py", "portalapi", "getassignments"],
            "args": ["expname"]
          },
          "failFatal": true,
          "formatFatal": false,
          "host": "login",
          "loop": false,
          "regex": [
            "(?P<name>[a-zA-Z0-9_]+),(?P<type>[a-zA-Z0-9_]+),(?P<jsonconfig>.*),(?P<amqpuri>.*),(?P<amqpcert>.*),(?P<amqpnoverifypeer>.*),(?P<amqpnoverifyhost>.*),(?P<uri>.*),(?P<cert>.*),(?P<noverifypeer>.*),(?P<noverifyhost>.*)"
          ],
          "requireMatch": true
        }
      }
    }
  }
]
```

The only part that matters is the dictionary ("`/1`", "`.[1]`"). The rest is ignored. It is unclear why it is still there.

### Defining Tasks

Each key is a _configuration_, and maps to the `{configuration}` parameter above.
Within each configuration are a list of commands ("`/1/Nimrod/Commands`", "`.[1].Nimrod.Commands`").

Each key in the `Commands` list maps to the `{task}` parameter. Internally, the keys are converted to lowercase and the endpoints become case-insensitive.

The `exec` and `cmd` keys are of some importance.

`cmd` existed first and is the legacy way to add commands. It takes a string with substitutions of the form `{variable_name}`. After substitutions are applied, this string is passed directly to a remote `bash -s` process and thus is vulnerable to injection flaws. It is **STRONGLY** recommended to not create tasks of this form.

`exec` is the new way to define commands (added by yours truly). It's an object with two fields: `command`, and `args`.

- `command` contains a list of strings that is directly executed on the remote system. Each string may **NOT** contain any substitutions. It is executed with the command:

  > ```
  > ssh <options> -- arg0 [arg1 [arg2 [argn...]]]
  > ```

- `args` is a list of argument names that the command expects. These are compiled into a JSON object and passed to the job's STDIN.
For example, if `args` was `["expname"]`, then `{"expname": "experiment1"}` is passed to STDIN.

If both `exec` and `cmd` are specified, `exec` always takes preference.

The `regex` key is used to handle output.
It's a Java regex with named captures that is applied to each line of the command's combined STDOUT and STDERR. If a line matches, the captured fields are put into a JSON object.

## Request and Response

With the exception of `{configuration}`, `{task}`, and `{host}`, all parameters are passed via the query string. It is expected that everything is escaped correctly.

Responses are of the format:
```json
{
  "userMessages": [
    "error1",
    "error2"
  ],
  "commandResult": [
    {
      "match0": "value",
      "match1": "value"
    },
    {
      "match0": "value2",
      "match1": "value2"
    }
  ]
}
```

`userMessages` is a list of error strings that should be displayed to the user.
`commandResult` is a list of objects containing the matched fields in the regex.

## Example

If an endpoint `accessibleLocations` were defined as follows:
```json
{
  "accessibleLocations": {
    "async": false,
    "exec": {
      "command": ["/opt/nimrod-portal/bin/nimptool", "getdirs"],
      "args": []
    },
    "failFatal": true,
    "formatFatal": false,
    "host": "login",
    "loop": false,
    "regex": ["^\\s*(?P<path>\\S+)\\s*$"],
    "requireMatch": true
  }
}
```

You would get the following:
```
$ curl -sv localhost:8082/nimbackend/api/execute/accessiblelocations | jq .
*   Trying ::1...
* TCP_NODELAY set
* connect to ::1 port 8082 failed: Connection refused
*   Trying 127.0.0.1...
* TCP_NODELAY set
* Connected to localhost (127.0.0.1) port 8082 (#0)
> GET /nimbackend/api/execute/accessiblelocations HTTP/1.1
> Host: localhost:8082
> User-Agent: curl/7.58.0
> Accept: */*
> 
< HTTP/1.1 200 
< Access-Control-Allow-Origin: *
< Access-Control-Allow-Methods: POST, PUT, GET, OPTIONS, DELETE
< Access-Control-Allow-Headers: Authorization, Content-Type
< Access-Control-Max-Age: 3600
< X-Content-Type-Options: nosniff
< X-XSS-Protection: 1; mode=block
< Cache-Control: no-cache, no-store, max-age=0, must-revalidate
< Pragma: no-cache
< Expires: 0
< X-Frame-Options: DENY
< Content-Type: application/json
< Transfer-Encoding: chunked
< Date: Wed, 11 Dec 2019 06:57:13 GMT
< 
{ [206 bytes data]
* Connection #0 to host localhost left intact
{
  "userMessages": [],
  "commandResult": [
    {
      "path": "/home/uquser"
    },
    {
      "path": "/30days/uquser"
    },
    {
      "path": "/90days/uquser"
    },
    {
      "path": "/QRISdata/Q0123"
    },
    {
      "path": "/QRISdata/Q0234"
    },
    {
      "path": "/QRISdata/Q345"
    }
  ]
}
```
