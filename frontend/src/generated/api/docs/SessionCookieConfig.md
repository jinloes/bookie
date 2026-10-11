# SessionCookieConfig

## Properties

| Name         | Type                       |
| ------------ | -------------------------- |
| `name`       | string                     |
| `path`       | string                     |
| `attributes` | { [key: string]: string; } |
| `comment`    | string                     |
| `maxAge`     | number                     |
| `httpOnly`   | boolean                    |
| `secure`     | boolean                    |
| `domain`     | string                     |

## Example

```typescript
import type { SessionCookieConfig } from '';

// TODO: Update the object below with actual values
const example = {
  name: null,
  path: null,
  attributes: null,
  comment: null,
  maxAge: null,
  httpOnly: null,
  secure: null,
  domain: null,
} satisfies SessionCookieConfig;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionCookieConfig;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
