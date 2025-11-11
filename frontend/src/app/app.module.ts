import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LoginComponent } from './auth/login.component';
import { MonitorsComponent } from './monitors/monitors.component';
import { AuthInterceptor } from './auth/auth.interceptor';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'monitors', component: MonitorsComponent },
  { path: '', redirectTo: 'monitors', pathMatch: 'full' },
  { path: '**', redirectTo: 'monitors' }
];

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    MonitorsComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule.forRoot(routes)
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}

