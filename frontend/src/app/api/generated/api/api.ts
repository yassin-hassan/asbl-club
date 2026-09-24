export * from './account.service';
import { AccountService } from './account.service';
export * from './authentication.service';
import { AuthenticationService } from './authentication.service';
export * from './public.service';
import { PublicService } from './public.service';
export const APIS = [AccountService, AuthenticationService, PublicService];
