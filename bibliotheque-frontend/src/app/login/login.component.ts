import { Component, OnInit } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { UserAuthService } from '../_service/user-auth.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  // Message posé par AuthInterceptor lors d'une redirection sur 401 (ex. session
  // expirée), affiché tel quel. Lu sur la navigation en cours : il disparaît si
  // l'on recharge /login, ce qui est voulu.
  messageSession: string | null = null;

  constructor(private userService: UsersService,
    private userAuthSerivce: UserAuthService,
    private router: Router
  ) {
    this.messageSession = this.router.getCurrentNavigation()?.extras.state?.['message'] ?? null;
  }

  ngOnInit() {
  }

  login(loginForm: NgForm) {
    this.userService.login(loginForm.value).subscribe(
      (response: any)=>{
        this.userAuthSerivce.setRoles(response.user.role);
        this.userAuthSerivce.setToken(response.jwtToken);
        this.userAuthSerivce.setUserId(response.user.userId);
        this.userAuthSerivce.setName(response.user.name);

        const role = response.user.role[0].roleName;
        if(role === 'Admin') {
          this.router.navigate(['/books']);
        } else {
          this.router.navigate(['/borrow-book']) //update later
        }
      },
      (error)=>{
        console.log(error);
      }
    );
  }

}