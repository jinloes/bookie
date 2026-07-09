
# ServletContext


## Properties

Name | Type
------------ | -------------
`classLoader` | [ApplicationContextClassLoaderParentUnnamedModuleClassLoader](ApplicationContextClassLoaderParentUnnamedModuleClassLoader.md)
`majorVersion` | number
`minorVersion` | number
`effectiveMajorVersion` | number
`effectiveMinorVersion` | number
`servletRegistrations` | [{ [key: string]: ServletRegistration; }](ServletRegistration.md)
`sessionTrackingModes` | Set&lt;string&gt;
`defaultSessionTrackingModes` | Set&lt;string&gt;
`requestCharacterEncoding` | string
`responseCharacterEncoding` | string
`effectiveSessionTrackingModes` | Set&lt;string&gt;
`initParameterNames` | any
`servletContextName` | string
`filterRegistrations` | [{ [key: string]: FilterRegistration; }](FilterRegistration.md)
`sessionCookieConfig` | [SessionCookieConfig](SessionCookieConfig.md)
`jspConfigDescriptor` | [JspConfigDescriptor](JspConfigDescriptor.md)
`virtualServerName` | string
`sessionTimeout` | number
`attributeNames` | any
`serverInfo` | string
`contextPath` | string

## Example

```typescript
import type { ServletContext } from ''

// TODO: Update the object below with actual values
const example = {
  "classLoader": null,
  "majorVersion": null,
  "minorVersion": null,
  "effectiveMajorVersion": null,
  "effectiveMinorVersion": null,
  "servletRegistrations": null,
  "sessionTrackingModes": null,
  "defaultSessionTrackingModes": null,
  "requestCharacterEncoding": null,
  "responseCharacterEncoding": null,
  "effectiveSessionTrackingModes": null,
  "initParameterNames": null,
  "servletContextName": null,
  "filterRegistrations": null,
  "sessionCookieConfig": null,
  "jspConfigDescriptor": null,
  "virtualServerName": null,
  "sessionTimeout": null,
  "attributeNames": null,
  "serverInfo": null,
  "contextPath": null,
} satisfies ServletContext

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ServletContext
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


