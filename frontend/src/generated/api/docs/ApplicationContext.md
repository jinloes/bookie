# ApplicationContext

## Properties

| Name                         | Type                                                              |
| ---------------------------- | ----------------------------------------------------------------- |
| `parent`                     | any                                                               |
| `id`                         | string                                                            |
| `displayName`                | string                                                            |
| `autowireCapableBeanFactory` | any                                                               |
| `applicationName`            | string                                                            |
| `startupDate`                | number                                                            |
| `environment`                | [Environment](Environment.md)                                     |
| `beanDefinitionCount`        | number                                                            |
| `beanDefinitionNames`        | Array&lt;string&gt;                                               |
| `parentBeanFactory`          | any                                                               |
| `classLoader`                | [ApplicationContextClassLoader](ApplicationContextClassLoader.md) |

## Example

```typescript
import type { ApplicationContext } from '';

// TODO: Update the object below with actual values
const example = {
  parent: null,
  id: null,
  displayName: null,
  autowireCapableBeanFactory: null,
  applicationName: null,
  startupDate: null,
  environment: null,
  beanDefinitionCount: null,
  beanDefinitionNames: null,
  parentBeanFactory: null,
  classLoader: null,
} satisfies ApplicationContext;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ApplicationContext;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
