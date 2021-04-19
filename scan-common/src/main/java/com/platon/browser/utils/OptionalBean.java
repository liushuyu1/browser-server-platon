package com.platon.browser.utils;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 基于Optional的链式判空
 *
 * @author huangyongpeng@matrixelements.com
 * @date 2021/4/19
 */
public final class OptionalBean<T> {

    private static final OptionalBean<?> EMPTY = new OptionalBean<>();

    private final T value;

    private OptionalBean() {
        this.value = null;
    }

    /**
     * 空值会抛出空指针
     *
     * @param value
     * @return
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    private OptionalBean(T value) {
        this.value = Objects.requireNonNull(value);
    }

    /**
     * 包装一个不能为空的bean
     *
     * @param value
     * @return com.platon.browser.utils.OptionalBean<T>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public static <T> OptionalBean<T> of(T value) {
        return new OptionalBean<>(value);
    }

    /**
     * 包装一个可能为空的bean
     *
     * @param value
     * @return com.platon.browser.utils.OptionalBean<T>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public static <T> OptionalBean<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    /**
     * 空值常量
     *
     * @param
     * @return com.platon.browser.utils.OptionalBean<T>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public static <T> OptionalBean<T> empty() {
        @SuppressWarnings("unchecked")
        OptionalBean<T> none = (OptionalBean<T>) EMPTY;
        return none;
    }

    /**
     * 取出具体的值
     *
     * @param
     * @return T
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public T get() {
        return Objects.isNull(value) ? null : value;
    }

    /**
     * 取出一个可能为空的对象
     *
     * @param fn
     * @return com.platon.browser.utils.OptionalBean<R>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public <R> OptionalBean<R> getNullableBean(Function<? super T, ? extends R> fn) {
        return Objects.isNull(value) ? OptionalBean.empty() : OptionalBean.ofNullable(fn.apply(value));
    }

    /**
     * 如果目标值为空,获取一个默认值
     *
     * @param other
     * @return T
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public T orElse(T other) {
        return value != null ? value : other;
    }

    /**
     * 如果目标值为空,通过lambda表达式获取一个值
     *
     * @param other
     * @return T
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public T orElseGet(Supplier<? extends T> other) {
        return value != null ? value : other.get();
    }

    /**
     * 如果目标值为空,抛出一个异常
     *
     * @param exceptionSupplier
     * @return T
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (value != null) {
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }

    /**
     * 是否为空
     *
     * @param
     * @return boolean
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * 是否为空
     *
     * @param consumer
     * @return void
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/19
     */
    public void ifPresent(Consumer<? super T> consumer) {
        if (value != null)
            consumer.accept(value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }


}
