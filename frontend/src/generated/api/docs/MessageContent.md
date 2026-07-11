# MessageContent

## Properties

| Name           | Type   |
| -------------- | ------ |
| `subject`      | string |
| `body`         | string |
| `receivedDate` | string |

## Example

```typescript
import type { MessageContent } from '';

// TODO: Update the object below with actual values
const example = {
  subject: null,
  body: null,
  receivedDate: null,
} satisfies MessageContent;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MessageContent;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
