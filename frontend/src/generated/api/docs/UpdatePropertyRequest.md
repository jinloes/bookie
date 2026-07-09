
# UpdatePropertyRequest


## Properties

Name | Type
------------ | -------------
`name` | string
`address` | string
`type` | string
`notes` | string
`accounts` | Set&lt;string&gt;

## Example

```typescript
import type { UpdatePropertyRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "name": null,
  "address": null,
  "type": null,
  "notes": null,
  "accounts": null,
} satisfies UpdatePropertyRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdatePropertyRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


