FROM php:8.2-fpm

ARG APP_ENV=production
ENV APP_ENV=${APP_ENV}

RUN apt-get update && apt-get install -y \
    git \
    zip \
    unzip \
    libpng-dev \
    libjpeg-dev \
    libfreetype6-dev \
    libonig-dev \
    libxml2-dev \
    && docker-php-ext-install pdo_mysql mbstring exif pcntl bcmath gd

# Enable Xdebug for development only
RUN if [ "$APP_ENV" = "development" ]; then \
      pecl install xdebug && docker-php-ext-enable xdebug; \
    fi

WORKDIR /var/www/html

# Copy application code
COPY . /var/www/html
COPY ./includes /var/www/html/includes

RUN chown -R www-data:www-data /var/www/html
