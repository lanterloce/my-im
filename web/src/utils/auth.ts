const ACCESS='access_token'; const REFRESH='refresh_token';
export const getAccessToken=()=>localStorage.getItem(ACCESS);
export const getRefreshToken=()=>localStorage.getItem(REFRESH);
export const saveTokens=(a:string,r:string)=>{localStorage.setItem(ACCESS,a);localStorage.setItem(REFRESH,r)};
export const clearTokens=()=>{localStorage.removeItem(ACCESS);localStorage.removeItem(REFRESH)};
