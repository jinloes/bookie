# ApplicationContextClassLoaderParentUnnamedModuleClassLoader

## Properties

| Name                          | Type                                                                                                                                                                               |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `name`                        | string                                                                                                                                                                             |
| `registeredAsParallelCapable` | boolean                                                                                                                                                                            |
| `definedPackages`             | [Array&lt;ApplicationContextClassLoaderParentUnnamedModuleClassLoaderDefinedPackagesInner&gt;](ApplicationContextClassLoaderParentUnnamedModuleClassLoaderDefinedPackagesInner.md) |
| `defaultAssertionStatus`      | boolean                                                                                                                                                                            |

## Example

```typescript
import type { ApplicationContextClassLoaderParentUnnamedModuleClassLoader } from '';

// TODO: Update the object below with actual values
const example = {
  name: null,
  registeredAsParallelCapable: null,
  definedPackages: null,
  defaultAssertionStatus: null,
} satisfies ApplicationContextClassLoaderParentUnnamedModuleClassLoader;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(
  exampleJSON
) as ApplicationContextClassLoaderParentUnnamedModuleClassLoader;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
