#include <dlfcn.h>
#include <stdio.h>

int main(int argc, char** argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s /path/to/effect.so\n", argv[0]);
        return 2;
    }

    void* library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (library == NULL) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 3;
    }

    const char* symbols[] = {"createEffect", "queryEffect", "destroyEffect"};
    for (size_t index = 0; index < sizeof(symbols) / sizeof(symbols[0]); ++index) {
        dlerror();
        if (dlsym(library, symbols[index]) == NULL) {
            fprintf(stderr, "missing %s: %s\n", symbols[index], dlerror());
            dlclose(library);
            return 4;
        }
    }

    dlclose(library);
    puts("AudioFreedom effect library loaded; all entry points resolved.");
    return 0;
}
