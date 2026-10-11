# ServletContext

## Properties

| Name                            | Type                                                                                                                          |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `classLoader`                   | [ApplicationContextClassLoaderParentUnnamedModuleClassLoader](ApplicationContextClassLoaderParentUnnamedModuleClassLoader.md) |
| `majorVersion`                  | number                                                                                                                        |
| `minorVersion`                  | number                                                                                                                        |
| `serverInfo`                    | string                                                                                                                        |
| `contextPath`                   | string                                                                                                                        |
| `sessionCookieConfig`           | [SessionCookieConfig](SessionCookieConfig.md)                                                                                 |
| `virtualServerName`             | string                                                                                                                        |
| `servletContextName`            | string                                                                                                                        |
| `filterRegistrations`           | [{ [key: string]: FilterRegistration; }](FilterRegistration.md)                                                               |
| `jspConfigDescriptor`           | [JspConfigDescriptor](JspConfigDescriptor.md)                                                                                 |
| `sessionTimeout`                | number                                                                                                                        |
| `attributeNames`                | any                                                                                                                           |
| `initParameterNames`            | any                                                                                                                           |
| `effectiveSessionTrackingModes` | Set&lt;string&gt;                                                                                                             |
| `effectiveMajorVersion`         | number                                                                                                                        |
| `effectiveMinorVersion`         | number                                                                                                                        |
| `servletRegistrations`          | [{ [key: string]: ServletRegistration; }](ServletRegistration.md)                                                             |
| `sessionTrackingModes`          | Set&lt;string&gt;                                                                                                             |
| `defaultSessionTrackingModes`   | Set&lt;string&gt;                                                                                                             |
| `requestCharacterEncoding`      | string                                                                                                                        |
| `responseCharacterEncoding`     | string                                                                                                                        |

## Example

```typescript
import type { ServletContext } from '';

// TODO: Update the object below with actual values
const example = {
  classLoader: null,
  majorVersion: null,
  minorVersion: null,
  serverInfo: null,
  contextPath: null,
  sessionCookieConfig: null,
  virtualServerName: null,
  servletContextName: null,
  filterRegistrations: null,
  jspConfigDescriptor: null,
  sessionTimeout: null,
  attributeNames: null,
  initParameterNames: null,
  effectiveSessionTrackingModes: null,
  effectiveMajorVersion: null,
  effectiveMinorVersion: null,
  servletRegistrations: null,
  sessionTrackingModes: null,
  defaultSessionTrackingModes: null,
  requestCharacterEncoding: null,
  responseCharacterEncoding: null,
} satisfies ServletContext;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ServletContext;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
