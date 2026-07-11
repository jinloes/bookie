# RedirectViewStatusCode

## Properties

| Name                 | Type    |
| -------------------- | ------- |
| `error`              | boolean |
| `is4xxClientError`   | boolean |
| `is5xxServerError`   | boolean |
| `is1xxInformational` | boolean |
| `is2xxSuccessful`    | boolean |
| `is3xxRedirection`   | boolean |

## Example

```typescript
import type { RedirectViewStatusCode } from '';

// TODO: Update the object below with actual values
const example = {
  error: null,
  is4xxClientError: null,
  is5xxServerError: null,
  is1xxInformational: null,
  is2xxSuccessful: null,
  is3xxRedirection: null,
} satisfies RedirectViewStatusCode;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RedirectViewStatusCode;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
