# RedirectView

## Properties

| Name                             | Type                                                |
| -------------------------------- | --------------------------------------------------- |
| `applicationContext`             | [ApplicationContext](ApplicationContext.md)         |
| `servletContext`                 | [ServletContext](ServletContext.md)                 |
| `contentType`                    | string                                              |
| `requestContextAttribute`        | string                                              |
| `staticAttributes`               | { [key: string]: any; }                             |
| `exposePathVariables`            | boolean                                             |
| `exposeContextBeansAsAttributes` | boolean                                             |
| `exposedContextBeanNames`        | Array&lt;string&gt;                                 |
| `beanName`                       | string                                              |
| `url`                            | string                                              |
| `contextRelative`                | boolean                                             |
| `http10Compatible`               | boolean                                             |
| `exposeModelAttributes`          | boolean                                             |
| `encodingScheme`                 | string                                              |
| `statusCode`                     | [RedirectViewStatusCode](RedirectViewStatusCode.md) |
| `expandUriTemplateVariables`     | boolean                                             |
| `propagateQueryParams`           | boolean                                             |
| `hosts`                          | Array&lt;string&gt;                                 |
| `propagateQueryProperties`       | boolean                                             |
| `redirectView`                   | boolean                                             |
| `attributesCSV`                  | string                                              |
| `attributesMap`                  | { [key: string]: any; }                             |
| `attributes`                     | { [key: string]: string; }                          |

## Example

```typescript
import type { RedirectView } from '';

// TODO: Update the object below with actual values
const example = {
  applicationContext: null,
  servletContext: null,
  contentType: null,
  requestContextAttribute: null,
  staticAttributes: null,
  exposePathVariables: null,
  exposeContextBeansAsAttributes: null,
  exposedContextBeanNames: null,
  beanName: null,
  url: null,
  contextRelative: null,
  http10Compatible: null,
  exposeModelAttributes: null,
  encodingScheme: null,
  statusCode: null,
  expandUriTemplateVariables: null,
  propagateQueryParams: null,
  hosts: null,
  propagateQueryProperties: null,
  redirectView: null,
  attributesCSV: null,
  attributesMap: null,
  attributes: null,
} satisfies RedirectView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RedirectView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
