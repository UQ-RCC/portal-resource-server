# resource-server

To use a custom shell, see below:
```$
-Dportal.ssh_shell.0=/path/to/my/shell
-Dportal.ssh_shell.1=arg1
...
-Dportal.ssh_shell.n=argn
```

Will stop at the first `NULL` property.

Defaults to `bash -s`.

