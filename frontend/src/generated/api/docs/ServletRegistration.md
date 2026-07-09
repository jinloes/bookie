
# ServletRegistration


## Properties

Name | Type
------------ | -------------
`mappings` | Array&lt;string&gt;
`runAsRole` | string
`name` | string
`className` | string
`initParameters` | { [key: string]: string; }

## Example

```typescript
import type { ServletRegistration } from ''

// TODO: Update the object below with actual values
const example = {
  "mappings": null,
  "runAsRole": null,
  "name": null,
  "className": null,
  "initParameters": null,
} satisfies ServletRegistration

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ServletRegistration
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


